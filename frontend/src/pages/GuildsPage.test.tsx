import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { api } from '../api/client';
import { renderWithRouter } from '../test/utils';
import GuildsPage from './GuildsPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    api: {
      listAuthorizedGuilds: vi.fn(),
      listEligibleGuilds: vi.fn(),
      onboardGuild: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('GuildsPage', () => {
  it('renders active and eligible guilds once loaded', async () => {
    mockedApi.listAuthorizedGuilds.mockResolvedValue([
      { guildId: '100', name: 'Active Guild', role: 'OWNER' },
    ]);
    mockedApi.listEligibleGuilds.mockResolvedValue([
      {
        guildId: '200',
        name: 'Eligible Guild',
        iconHash: null,
        discordRole: 'ADMIN',
        onboardingStatus: 'AVAILABLE',
      },
    ]);

    renderWithRouter(<GuildsPage />);

    expect(await screen.findByText('Active Guild')).toBeInTheDocument();
    expect(screen.getByText('Eligible Guild')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Onboard' })).toBeInTheDocument();
    expect(screen.getByText('Available')).toBeInTheDocument();
  });

  it('onboards an eligible guild and refreshes the lists with CSRF-backed call', async () => {
    const user = userEvent.setup();

    mockedApi.listAuthorizedGuilds
      .mockResolvedValueOnce([])
      .mockResolvedValue([{ guildId: '200', name: 'Eligible Guild', role: 'ADMIN' }]);
    mockedApi.listEligibleGuilds
      .mockResolvedValueOnce([
        {
          guildId: '200',
          name: 'Eligible Guild',
          iconHash: null,
          discordRole: 'ADMIN',
          onboardingStatus: 'AVAILABLE',
        },
      ])
      .mockResolvedValue([
        {
          guildId: '200',
          name: 'Eligible Guild',
          iconHash: null,
          discordRole: 'ADMIN',
          onboardingStatus: 'ONBOARDED',
        },
      ]);
    mockedApi.onboardGuild.mockResolvedValue({ guildId: '200', name: 'Eligible Guild', role: 'ADMIN' });

    renderWithRouter(<GuildsPage />);

    const onboardButton = await screen.findByRole('button', { name: 'Onboard' });
    await user.click(onboardButton);

    expect(mockedApi.onboardGuild).toHaveBeenCalledWith('200');
    expect(await screen.findByText(/is now onboarded/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(mockedApi.listAuthorizedGuilds).toHaveBeenCalledTimes(2);
    });
  });

  it('shows an error state when loading fails', async () => {
    mockedApi.listAuthorizedGuilds.mockRejectedValue(new Error('network'));
    mockedApi.listEligibleGuilds.mockRejectedValue(new Error('network'));

    renderWithRouter(<GuildsPage />);

    expect(await screen.findByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
