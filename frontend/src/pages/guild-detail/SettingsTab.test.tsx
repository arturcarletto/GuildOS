import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import { renderWithRouter } from '../../test/utils';
import SettingsTab from './SettingsTab';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof import('../../api/client')>('../../api/client');
  return {
    ...actual,
    api: {
      getGuildSettings: vi.fn(),
      updateGuildSettings: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('SettingsTab conflict handling', () => {
  it('shows a conflict message and reloads the latest settings on 409', async () => {
    const user = userEvent.setup();

    mockedApi.getGuildSettings
      .mockResolvedValueOnce({
        guildId: '9',
        name: 'Guild',
        timezone: 'UTC',
        locale: 'en-US',
        version: 0,
        updatedAt: '2026-07-03T00:00:00Z',
      })
      // After the conflict, the reload returns the newer server state.
      .mockResolvedValue({
        guildId: '9',
        name: 'Guild',
        timezone: 'America/Sao_Paulo',
        locale: 'pt-BR',
        version: 3,
        updatedAt: '2026-07-04T00:00:00Z',
      });

    mockedApi.updateGuildSettings.mockRejectedValueOnce(
      new ApiError(409, { error: 'conflict' }),
    );

    renderWithRouter(<SettingsTab guildId="9" />);

    const timezoneInput = await screen.findByLabelText('Timezone');
    await user.clear(timezoneInput);
    await user.type(timezoneInput, 'Europe/Paris');

    await user.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(await screen.findByText(/Settings changed elsewhere/i)).toBeInTheDocument();

    // The reload pulls the newer version and re-syncs the form.
    await waitFor(() => {
      expect(mockedApi.getGuildSettings).toHaveBeenCalledTimes(2);
    });
    expect(await screen.findByDisplayValue('America/Sao_Paulo')).toBeInTheDocument();
    expect(screen.getByText(/Version 3/i)).toBeInTheDocument();
  });

  it('saves successfully and updates the version indicator', async () => {
    const user = userEvent.setup();

    mockedApi.getGuildSettings.mockResolvedValue({
      guildId: '9',
      name: 'Guild',
      timezone: 'UTC',
      locale: 'en-US',
      version: 1,
      updatedAt: '2026-07-03T00:00:00Z',
    });
    mockedApi.updateGuildSettings.mockResolvedValue({
      guildId: '9',
      name: 'Guild',
      timezone: 'America/Sao_Paulo',
      locale: 'en-US',
      version: 2,
      updatedAt: '2026-07-05T00:00:00Z',
    });

    renderWithRouter(<SettingsTab guildId="9" />);

    const timezoneInput = await screen.findByLabelText('Timezone');
    await user.clear(timezoneInput);
    await user.type(timezoneInput, 'America/Sao_Paulo');
    await user.click(screen.getByRole('button', { name: 'Save settings' }));

    expect(await screen.findByText('Settings saved.')).toBeInTheDocument();
    expect(mockedApi.updateGuildSettings).toHaveBeenCalledWith('9', {
      timezone: 'America/Sao_Paulo',
      locale: 'en-US',
      expectedVersion: 1,
    });
    expect(screen.getByText(/Version 2/i)).toBeInTheDocument();
  });
});
