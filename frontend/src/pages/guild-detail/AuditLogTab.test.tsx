import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api } from '../../api/client';
import type { GuildAuditLog } from '../../api/types';
import AuditLogTab from './AuditLogTab';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof import('../../api/client')>('../../api/client');
  return {
    ...actual,
    api: {
      getGuildAuditLog: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const AUDIT_LOG: GuildAuditLog = {
  guildId: 'g1',
  events: [
    {
      occurredAt: '2026-01-02T03:04:05Z',
      eventType: 'WELCOME_CONFIGURED',
      actorType: 'OPERATOR',
      summary: 'Welcome message configuration was updated.',
      targetType: 'WELCOME_MESSAGE',
      targetLabel: 'Welcome automation',
    },
    {
      occurredAt: '2026-01-02T04:04:05Z',
      eventType: 'CHANNEL_METADATA_SYNCED',
      actorType: 'SYSTEM',
      summary: 'Discord channel metadata was refreshed.',
      targetType: 'CHANNEL_SYNC',
      targetLabel: 'Channel metadata',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.getGuildAuditLog.mockResolvedValue(AUDIT_LOG);
});

describe('AuditLogTab', () => {
  it('renders audit events with labels and safe fields', async () => {
    render(<AuditLogTab guildId="g1" />);

    expect(await screen.findByText('Welcome Configured')).toBeInTheDocument();
    expect(screen.getByText('Channel Metadata Synced')).toBeInTheDocument();
    expect(screen.getByText('Operator')).toBeInTheDocument();
    expect(screen.getByText('System')).toBeInTheDocument();
    expect(screen.getByText('Welcome message configuration was updated.')).toBeInTheDocument();
    expect(screen.getByText('Welcome automation')).toBeInTheDocument();
    expect(mockedApi.getGuildAuditLog).toHaveBeenCalledWith('g1', { limit: 50 });
    expect(screen.queryByText('internal-event-id')).not.toBeInTheDocument();
  });

  it('renders an empty state when no events exist', async () => {
    mockedApi.getGuildAuditLog.mockResolvedValueOnce({ guildId: 'g1', events: [] });

    render(<AuditLogTab guildId="g1" />);

    expect(await screen.findByText('No audit events yet.')).toBeInTheDocument();
  });

  it('renders a safe error state when loading fails', async () => {
    mockedApi.getGuildAuditLog.mockRejectedValueOnce(new ApiError(500, { error: 'server_error' }));

    render(<AuditLogTab guildId="g1" />);

    expect(await screen.findByText('The server ran into a problem. Please try again shortly.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
