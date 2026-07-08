import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import AppShell from './AppShell';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    api: {
      getCurrentOperator: vi.fn(),
      listAuthorizedGuilds: vi.fn(),
      logout: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

function renderApp(initialPath = '/dashboard') {
  return render(
    <MemoryRouter
      initialEntries={[initialPath]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <AuthProvider>
        <Routes>
          <Route path="/" element={<div>Landing screen</div>} />
          <Route path="/dashboard" element={<AppShell />}>
            <Route index element={<div>Dashboard content</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.listAuthorizedGuilds.mockResolvedValue([]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('AppShell', () => {
  it('redirects an unauthenticated visitor to the landing page without render-side navigation', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    mockedApi.getCurrentOperator.mockRejectedValue(new ApiError(401, { error: 'unauthorized' }));

    renderApp();

    expect(await screen.findByText('Landing screen')).toBeInTheDocument();
    expect(screen.queryByText('Dashboard content')).not.toBeInTheDocument();

    // The redirect must be declarative (<Navigate>), never a navigate() call during render.
    const renderPhaseWarning = consoleError.mock.calls
      .flat()
      .map((entry) => String(entry))
      .some((message) => message.includes('while rendering a different component'));
    expect(renderPhaseWarning).toBe(false);
  });

  it('renders the dashboard shell for an authenticated operator', async () => {
    mockedApi.getCurrentOperator.mockResolvedValue({
      operatorId: 'op-1',
      discordUserId: 'discord-1',
      username: 'ada',
      displayName: 'Ada Lovelace',
      avatarHash: null,
    });

    renderApp();

    expect(await screen.findByText('Dashboard content')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    expect(screen.queryByText('Landing screen')).not.toBeInTheDocument();
  });
});
