import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import DashboardPage from './DashboardPage';

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

function renderDashboard(ui: ReactElement = <DashboardPage />) {
  return render(
    <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>{ui}</AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getCurrentOperator.mockResolvedValue({
    operatorId: 'op-1',
    discordUserId: 'discord-1',
    username: 'ada',
    displayName: 'Ada',
    avatarHash: null,
  });
});

describe('DashboardPage', () => {
  it('renders the operator active guilds when the request succeeds', async () => {
    mockedApi.listAuthorizedGuilds.mockResolvedValue([
      { guildId: '100', name: 'Active Guild', role: 'OWNER' },
    ]);

    renderDashboard();

    expect(await screen.findByText('Active Guild')).toBeInTheDocument();
    expect(screen.getByText('OWNER')).toBeInTheDocument();
  });

  it('shows a clean empty state when the operator has no onboarded guilds', async () => {
    mockedApi.listAuthorizedGuilds.mockResolvedValue([]);

    renderDashboard();

    expect(await screen.findByText(/no active guild authorizations yet/i)).toBeInTheDocument();
    // The count card should read 0, not an error.
    expect(screen.getByText('Active guild authorizations')).toBeInTheDocument();
  });

  it('renders an error state on a 500 and reloads when Try again is clicked', async () => {
    const user = userEvent.setup();
    mockedApi.listAuthorizedGuilds
      .mockRejectedValueOnce(new ApiError(500, { error: 'server_error' }))
      .mockResolvedValue([{ guildId: '200', name: 'Recovered Guild', role: 'ADMIN' }]);

    renderDashboard();

    expect(await screen.findByText(/the server ran into a problem/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Recovered Guild')).toBeInTheDocument();
    await waitFor(() => {
      expect(mockedApi.listAuthorizedGuilds).toHaveBeenCalledTimes(2);
    });
  });
});
