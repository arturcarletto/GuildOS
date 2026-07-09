import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import type { ModerationCasesResponse } from '../../api/types';
import ModerationTab from './ModerationTab';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof import('../../api/client')>('../../api/client');
  return {
    ...actual,
    api: {
      createMemberTimeout: vi.fn(),
      getModerationCases: vi.fn(),
      searchGuildMembers: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const CASE_HISTORY: ModerationCasesResponse = {
  guildId: 'g1',
  cases: [
    {
      publicCaseId: 'case_abc',
      actionType: 'MEMBER_TIMEOUT_CREATED',
      targetType: 'DISCORD_USER',
      targetUserId: '123456789012345678',
      durationMinutes: 10,
      status: 'COMPLETED',
      summary: 'Member timeout completed.',
      occurredAt: '2026-01-02T03:04:05Z',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.createMemberTimeout.mockResolvedValue({
    guildId: 'g1',
    actionType: 'MEMBER_TIMEOUT',
    targetUserId: '123456789012345678',
    durationMinutes: 10,
    status: 'SUCCEEDED',
  });
  mockedApi.getModerationCases.mockResolvedValue(CASE_HISTORY);
  mockedApi.searchGuildMembers.mockResolvedValue({
    guildId: 'g1',
    query: 'some',
    limit: 10,
    results: [
      { userId: '123456789012345678', username: 'some_user', displayName: 'Some User', bot: false },
    ],
  });
});

describe('ModerationTab', () => {
  it('renders the member timeout form, member search input, and case history', async () => {
    render(<ModerationTab guildId="g1" />);

    expect(screen.getByRole('heading', { name: 'Moderation' })).toBeInTheDocument();
    expect(screen.getByLabelText('Search by name or user ID')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Search' })).toBeInTheDocument();
    expect(screen.getByLabelText('Target Discord user ID')).toBeInTheDocument();
    expect(screen.getByLabelText('Duration minutes')).toBeInTheDocument();
    expect(screen.getByLabelText('Reason')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply timeout' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Recent moderation cases' })).toBeInTheDocument();
    expect(await screen.findByText('Member Timeout Created')).toBeInTheDocument();
  });

  it('shows a loading state and then renders search results', async () => {
    const user = userEvent.setup();
    let resolveSearch: (value: {
      guildId: string;
      query: string;
      limit: number;
      results: never[];
    }) => void = () => {};
    mockedApi.searchGuildMembers.mockReturnValue(
      new Promise((resolve) => {
        resolveSearch = resolve;
      }),
    );

    render(<ModerationTab guildId="g1" />);
    await user.type(screen.getByLabelText('Search by name or user ID'), 'some');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Searching members...')).toBeInTheDocument();

    resolveSearch({ guildId: 'g1', query: 'some', limit: 10, results: [] });
    await waitFor(() => {
      expect(screen.queryByText('Searching members...')).not.toBeInTheDocument();
    });
  });

  it('shows an empty state when no member matches', async () => {
    const user = userEvent.setup();
    mockedApi.searchGuildMembers.mockResolvedValue({
      guildId: 'g1',
      query: 'ghost',
      limit: 10,
      results: [],
    });

    render(<ModerationTab guildId="g1" />);
    await user.type(screen.getByLabelText('Search by name or user ID'), 'ghost');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('No members matched that search.')).toBeInTheDocument();
  });

  it('shows an error state when the search fails', async () => {
    const user = userEvent.setup();
    mockedApi.searchGuildMembers.mockRejectedValue(
      new ApiError(502, { error: 'guild_unavailable', message: 'The Discord guild is unavailable to the bot.' }),
    );

    render(<ModerationTab guildId="g1" />);
    await user.type(screen.getByLabelText('Search by name or user ID'), 'some');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(
      await screen.findByText('The Discord guild is unavailable to the bot.'),
    ).toBeInTheDocument();
  });

  it('validates the query length before calling the API', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Search by name or user ID'), 'a');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Search query must be at least 2 characters.')).toBeInTheDocument();
    expect(mockedApi.searchGuildMembers).not.toHaveBeenCalled();
  });

  it('rejects a single-digit query without treating it as a snowflake', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Search by name or user ID'), '1');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(
      await screen.findByText('Numeric search must be a full Discord user id (17-20 digits).'),
    ).toBeInTheDocument();
    expect(mockedApi.searchGuildMembers).not.toHaveBeenCalled();
  });

  it('rejects a short numeric query that is not a full snowflake', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Search by name or user ID'), '12');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(
      await screen.findByText('Numeric search must be a full Discord user id (17-20 digits).'),
    ).toBeInTheDocument();
    expect(mockedApi.searchGuildMembers).not.toHaveBeenCalled();
  });

  it('calls the search API for a full Discord snowflake id', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Search by name or user ID'), '123456789012345678');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(mockedApi.searchGuildMembers).toHaveBeenCalledWith('g1', '123456789012345678', 10);
    });
  });

  it('fills the target user id when a search result is selected', async () => {
    const user = userEvent.setup();
    render(<ModerationTab guildId="g1" />);

    await user.type(screen.getByLabelText('Search by name or user ID'), 'some');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    const result = await screen.findByRole('button', { name: /Some User/ });
    await user.click(result);

    expect(screen.getByLabelText<HTMLInputElement>('Target Discord user ID').value).toBe(
      '123456789012345678',
    );
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
    await waitFor(() => {
      expect(mockedApi.getModerationCases).toHaveBeenCalledTimes(2);
    });
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

  it('renders moderation cases with safe fields', async () => {
    render(<ModerationTab guildId="g1" />);

    expect(await screen.findByText('Member Timeout Created')).toBeInTheDocument();
    expect(screen.getByText('123456789012345678')).toBeInTheDocument();
    expect(screen.getByText('10 min')).toBeInTheDocument();
    expect(screen.getByText('Completed')).toBeInTheDocument();
    expect(screen.getByText('Member timeout completed.')).toBeInTheDocument();
    expect(mockedApi.getModerationCases).toHaveBeenCalledWith('g1', { limit: 50 });
    expect(screen.queryByText('internal-db-id')).not.toBeInTheDocument();
  });

  it('renders an empty moderation case state', async () => {
    mockedApi.getModerationCases.mockResolvedValueOnce({ guildId: 'g1', cases: [] });

    render(<ModerationTab guildId="g1" />);

    expect(await screen.findByText('No moderation cases yet.')).toBeInTheDocument();
  });

  it('renders a safe moderation case error state', async () => {
    mockedApi.getModerationCases.mockRejectedValueOnce(new ApiError(500, { error: 'server_error' }));

    render(<ModerationTab guildId="g1" />);

    expect(await screen.findByText('The server ran into a problem. Please try again shortly.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('does not render accidental internal moderation case fields', async () => {
    mockedApi.getModerationCases.mockResolvedValueOnce({
      guildId: 'g1',
      cases: [
        {
          ...CASE_HISTORY.cases[0],
          id: 'internal-db-id',
          registeredGuildId: 'internal-guild-id',
          operatorId: 'internal-operator-id',
          reason: 'raw reason',
          username: 'some_user',
          displayName: 'Some User',
          avatar: 'avatar',
        } as unknown as ModerationCasesResponse['cases'][number],
      ],
    });

    render(<ModerationTab guildId="g1" />);

    expect(await screen.findByText('Member Timeout Created')).toBeInTheDocument();
    expect(screen.queryByText('internal-db-id')).not.toBeInTheDocument();
    expect(screen.queryByText('internal-operator-id')).not.toBeInTheDocument();
    expect(screen.queryByText('raw reason')).not.toBeInTheDocument();
    expect(screen.queryByText('Some User')).not.toBeInTheDocument();
  });
});
