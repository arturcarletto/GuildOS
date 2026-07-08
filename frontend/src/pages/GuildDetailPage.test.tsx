import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { api } from '../api/client';
import type { MemberMessageConfig } from '../api/types';
import GuildDetailPage from './GuildDetailPage';

vi.mock('../api/client', async () => {
  const actual = await vi.importActual<typeof import('../api/client')>('../api/client');
  return {
    ...actual,
    api: {
      listAuthorizedGuilds: vi.fn(),
      getMemberMessageConfig: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

function renderPage() {
  return render(
    <MemoryRouter
      initialEntries={['/dashboard/guilds/123456789012345678']}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <Routes>
        <Route path="/dashboard/guilds/:discordGuildId" element={<GuildDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.listAuthorizedGuilds.mockResolvedValue([
    { guildId: '123456789012345678', name: 'Test Guild', role: 'OWNER' },
  ]);
  mockedApi.getMemberMessageConfig.mockImplementation((_guildId, kind) =>
    Promise.resolve({
      kind: kind === 'welcome' ? 'WELCOME' : 'GOODBYE',
      configured: false,
      enabled: false,
      channelId: '',
      title: '',
      message: '',
      color: '',
      imageUrl: '',
      footer: '',
      includeBots: false,
      mentionMember: null,
      buttonLabel: '',
      buttonUrl: '',
    } as MemberMessageConfig),
  );
});

describe('GuildDetailPage', () => {
  it('exposes an Automation tab that opens the welcome/goodbye cards', async () => {
    const user = userEvent.setup();
    renderPage();

    const automationTab = await screen.findByRole('tab', { name: 'Automation' });
    expect(automationTab).toBeInTheDocument();

    await user.click(automationTab);

    expect(await screen.findByRole('heading', { name: 'Welcome message' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Goodbye message' })).toBeInTheDocument();
  });
});
