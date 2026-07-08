import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import ModerationTab from './ModerationTab';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof import('../../api/client')>('../../api/client');
  return {
    ...actual,
    api: {
      createMemberTimeout: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.createMemberTimeout.mockResolvedValue({
    guildId: 'g1',
    actionType: 'MEMBER_TIMEOUT',
    targetUserId: '123456789012345678',
    durationMinutes: 10,
    status: 'SUCCEEDED',
  });
});

describe('ModerationTab', () => {
  it('renders the member timeout form', () => {
    render(<ModerationTab guildId="g1" />);

    expect(screen.getByRole('heading', { name: 'Moderation' })).toBeInTheDocument();
    expect(screen.getByLabelText('Target Discord user ID')).toBeInTheDocument();
    expect(screen.getByLabelText('Duration minutes')).toBeInTheDocument();
    expect(screen.getByLabelText('Reason')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply timeout' })).toBeInTheDocument();
  });

  it('handles invalid fields without calling the API', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Target Discord user ID'), 'not-a-snowflake');
    await user.clear(screen.getByLabelText('Duration minutes'));
    await user.type(screen.getByLabelText('Duration minutes'), '10');
    await user.click(screen.getByRole('button', { name: 'Apply timeout' }));

    expect(await screen.findByText('Target user id must be a Discord snowflake.')).toBeInTheDocument();
    expect(mockedApi.createMemberTimeout).not.toHaveBeenCalled();
  });

  it('submits a valid timeout and shows success', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Target Discord user ID'), '123456789012345678');
    await user.clear(screen.getByLabelText('Duration minutes'));
    await user.type(screen.getByLabelText('Duration minutes'), '15');
    await user.type(screen.getByLabelText('Reason'), 'Repeated spam');
    await user.click(screen.getByRole('button', { name: 'Apply timeout' }));

    await waitFor(() => {
      expect(mockedApi.createMemberTimeout).toHaveBeenCalledWith('g1', {
        targetUserId: '123456789012345678',
        durationMinutes: 15,
        reason: 'Repeated spam',
      });
    });
    expect(
      await screen.findByText('Member timeout created for 123456789012345678.'),
    ).toBeInTheDocument();
  });

  it('shows a controlled API error', async () => {
    const user = userEvent.setup();
    mockedApi.createMemberTimeout.mockRejectedValue(
      new ApiError(409, {
        error: 'bot_permission_missing',
        message: 'The bot cannot timeout members in this guild.',
      }),
    );

    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Target Discord user ID'), '123456789012345678');
    await user.click(screen.getByRole('button', { name: 'Apply timeout' }));

    expect(
      await screen.findByText('The bot cannot timeout members in this guild.'),
    ).toBeInTheDocument();
  });
});
