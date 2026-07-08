import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import type { MemberMessageConfig, MemberMessagePreview } from '../../api/types';
import AutomationTab from './AutomationTab';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof import('../../api/client')>('../../api/client');
  return {
    ...actual,
    api: {
      getMemberMessageConfig: vi.fn(),
      updateMemberMessageConfig: vi.fn(),
      toggleMemberMessageConfig: vi.fn(),
      previewMemberMessageConfig: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

function unconfigured(kind: 'WELCOME' | 'GOODBYE'): MemberMessageConfig {
  return {
    kind,
    configured: false,
    enabled: false,
    channelId: '',
    title: '',
    message: '',
    color: '',
    imageUrl: '',
    footer: '',
    includeBots: false,
    mentionMember: kind === 'WELCOME' ? null : null,
    buttonLabel: '',
    buttonUrl: '',
  };
}

function configuredWelcome(): MemberMessageConfig {
  return {
    kind: 'WELCOME',
    configured: true,
    enabled: true,
    channelId: '123456789012345678',
    title: 'Welcome {member}',
    message: 'Hi {member}',
    color: '#57F287',
    imageUrl: '',
    footer: 'Welcome • {server}',
    includeBots: false,
    mentionMember: true,
    buttonLabel: '',
    buttonUrl: '',
  };
}

/** Waits for a card's heading (it only renders once its config has loaded) and returns its region. */
async function getCard(heading: string): Promise<HTMLElement> {
  const title = await screen.findByRole('heading', { name: heading });
  return title.closest('.card') as HTMLElement;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getMemberMessageConfig.mockImplementation((_guildId, kind) =>
    Promise.resolve(unconfigured(kind === 'welcome' ? 'WELCOME' : 'GOODBYE')),
  );
});

describe('AutomationTab', () => {
  it('renders welcome-only fields on the welcome card but not on the goodbye card', async () => {
    render(<AutomationTab guildId="g1" />);

    const welcome = await getCard('Welcome message');
    const goodbye = await getCard('Goodbye message');

    // Welcome-only controls.
    expect(within(welcome).getByLabelText('Mention (ping) the new member')).toBeInTheDocument();
    expect(within(welcome).getByLabelText('Button label')).toBeInTheDocument();
    expect(within(welcome).getByLabelText('Button URL')).toBeInTheDocument();

    // Goodbye hides the welcome-only controls.
    expect(
      within(goodbye).queryByLabelText('Mention (ping) the new member'),
    ).not.toBeInTheDocument();
    expect(within(goodbye).queryByLabelText('Button label')).not.toBeInTheDocument();
  });

  it('saves the welcome configuration with the correct API call', async () => {
    const user = userEvent.setup();
    mockedApi.updateMemberMessageConfig.mockResolvedValue(configuredWelcome());

    render(<AutomationTab guildId="g1" />);
    const welcome = await getCard('Welcome message');

    await user.type(within(welcome).getByLabelText('Channel ID'), '123456789012345678');
    await user.type(within(welcome).getByLabelText('Message'), 'Welcome friends');
    await user.click(within(welcome).getByRole('button', { name: 'Create message' }));

    await waitFor(() => {
      expect(mockedApi.updateMemberMessageConfig).toHaveBeenCalledWith(
        'g1',
        'welcome',
        expect.objectContaining({ channelId: '123456789012345678', message: 'Welcome friends' }),
      );
    });
    expect(await within(welcome).findByText('Saved.')).toBeInTheDocument();
  });

  it('toggles an existing configuration via the toggle API', async () => {
    const user = userEvent.setup();
    mockedApi.getMemberMessageConfig.mockImplementation((_guildId, kind) =>
      Promise.resolve(
        kind === 'welcome' ? configuredWelcome() : unconfigured('GOODBYE'),
      ),
    );
    mockedApi.toggleMemberMessageConfig.mockResolvedValue({ ...configuredWelcome(), enabled: false });

    render(<AutomationTab guildId="g1" />);
    const welcome = await getCard('Welcome message');
    await waitFor(() =>
      expect(within(welcome).getByRole('button', { name: 'Disable' })).toBeInTheDocument(),
    );

    await user.click(within(welcome).getByRole('button', { name: 'Disable' }));

    await waitFor(() => {
      expect(mockedApi.toggleMemberMessageConfig).toHaveBeenCalledWith('g1', 'welcome');
    });
    expect(await within(welcome).findByText('Disabled.')).toBeInTheDocument();
  });

  it('renders returned preview data without sending a message', async () => {
    const user = userEvent.setup();
    const preview: MemberMessagePreview = {
      kind: 'WELCOME',
      title: 'Hi Sample Member',
      description: 'Welcome Sample Member to Sample Server!',
      color: '#57F287',
      imageUrl: null,
      footer: 'Welcome • Sample Server',
      memberCount: 1234,
      mentionMember: true,
      buttonLabel: null,
      buttonUrl: null,
    };
    mockedApi.previewMemberMessageConfig.mockResolvedValue(preview);

    render(<AutomationTab guildId="g1" />);
    const welcome = await getCard('Welcome message');

    await user.type(within(welcome).getByLabelText('Channel ID'), '123456789012345678');
    await user.type(within(welcome).getByLabelText('Message'), 'Welcome friends');
    await user.click(within(welcome).getByRole('button', { name: 'Preview' }));

    expect(await within(welcome).findByText('Welcome Sample Member to Sample Server!')).toBeInTheDocument();
    expect(within(welcome).getByText('Hi Sample Member')).toBeInTheDocument();
    expect(within(welcome).getByText(/Would mention the new member/)).toBeInTheDocument();
    expect(mockedApi.previewMemberMessageConfig).toHaveBeenCalledWith(
      'g1',
      'welcome',
      expect.objectContaining({ message: 'Welcome friends' }),
    );
  });

  it('shows a safe validation message when saving fails', async () => {
    const user = userEvent.setup();
    mockedApi.updateMemberMessageConfig.mockRejectedValue(
      new ApiError(400, { error: 'bad_request', message: 'The color must be a hexadecimal value such as #57F287' }),
    );

    render(<AutomationTab guildId="g1" />);
    const welcome = await getCard('Welcome message');

    await user.type(within(welcome).getByLabelText('Channel ID'), '123456789012345678');
    await user.type(within(welcome).getByLabelText('Message'), 'Hi');
    await user.click(within(welcome).getByRole('button', { name: 'Create message' }));

    expect(
      await within(welcome).findByText('The color must be a hexadecimal value such as #57F287'),
    ).toBeInTheDocument();
  });

  it('shows an error state when the config fails to load', async () => {
    mockedApi.getMemberMessageConfig.mockRejectedValue(new ApiError(500, { error: 'server_error' }));

    render(<AutomationTab guildId="g1" />);

    expect(await screen.findAllByRole('button', { name: 'Try again' })).not.toHaveLength(0);
  });
});
