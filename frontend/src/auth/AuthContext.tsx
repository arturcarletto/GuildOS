import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { ApiError, api, clearCsrfToken } from '../api/client';
import type { CurrentOperator } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthState {
  status: AuthStatus;
  operator: CurrentOperator | null;
  /** Set when the session probe failed for a reason other than being signed out. */
  error: string | null;
}

interface AuthContextValue extends AuthState {
  /** Re-probes `GET /api/v1/me`. Returns the resolved status. */
  refresh: () => Promise<AuthStatus>;
  /** CSRF-protected `POST /logout`; clears local session state regardless of outcome. */
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    status: 'loading',
    operator: null,
    error: null,
  });

  const refresh = useCallback(async (): Promise<AuthStatus> => {
    setState((prev) => ({ ...prev, status: prev.operator ? prev.status : 'loading', error: null }));
    try {
      const operator = await api.getCurrentOperator();
      setState({ status: 'authenticated', operator, error: null });
      return 'authenticated';
    } catch (error) {
      if (error instanceof ApiError && error.isUnauthorized) {
        setState({ status: 'unauthenticated', operator: null, error: null });
        return 'unauthenticated';
      }
      // Network or unexpected failure: treat as signed-out but surface a message.
      setState({
        status: 'unauthenticated',
        operator: null,
        error: 'Could not reach Guild OS. Check that the backend is running and try again.',
      });
      return 'unauthenticated';
    }
  }, []);

  const signOut = useCallback(async (): Promise<void> => {
    try {
      await api.logout();
    } catch {
      // Even if logout fails server-side, drop local state so the UI reflects signed-out.
    } finally {
      clearCsrfToken();
      setState({ status: 'unauthenticated', operator: null, error: null });
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, refresh, signOut }),
    [state, refresh, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components -- provider + hook are colocated by design
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
