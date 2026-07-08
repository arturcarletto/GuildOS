import { useCallback, useEffect, useState } from 'react';

import { ApiError } from '../api/client';

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | Error | null;
  /** Re-runs the loader. */
  reload: () => void;
  /** Replaces the current data locally (e.g. after a successful mutation). */
  setData: (next: T) => void;
}

/**
 * Runs an async loader on mount and whenever its dependencies change, tracking loading/error state.
 * The loader must be stable (wrap it in `useCallback`) or listed via `deps`.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [nonce, setNonce] = useState(0);

  const reload = useCallback(() => setNonce((value) => value + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    loader()
      .then((result) => {
        if (!cancelled) {
          setData(result);
        }
      })
      .catch((caught: unknown) => {
        if (cancelled) {
          return;
        }
        setError(caught instanceof Error ? caught : new Error('Unexpected error'));
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nonce, ...deps]);

  return { data, loading, error, reload, setData };
}

/** Maps an unknown error to a user-safe message. Never surfaces raw server bodies or tokens. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isUnauthorized) {
      return 'Your session has expired. Please sign in again.';
    }
    if (error.isForbidden) {
      return 'You do not have permission to do that.';
    }
    if (error.isNotFound) {
      return 'That resource is unavailable or you no longer have access to it.';
    }
    if (error.status >= 500) {
      return 'The server ran into a problem. Please try again shortly.';
    }
    return 'The request could not be completed. Please try again.';
  }
  return 'Could not reach Guild OS. Check your connection and try again.';
}
