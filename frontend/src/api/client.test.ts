import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, api, clearCsrfToken, getCsrfToken } from './client';

interface MockResponseInit {
  status?: number;
  json?: unknown;
  text?: string;
}

function mockResponse({ status = 200, json, text }: MockResponseInit): Response {
  const body = text ?? (json === undefined ? '' : JSON.stringify(json));
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => body,
  } as Response;
}

const CSRF_RESPONSE = mockResponse({
  json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'token-123' },
});

type FetchMock = ReturnType<typeof vi.fn>;

let fetchMock: FetchMock;

beforeEach(() => {
  clearCsrfToken();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** Reads the RequestInit passed to the Nth fetch call. */
function initOf(call: number): RequestInit {
  return fetchMock.mock.calls[call][1] as RequestInit;
}

function urlOf(call: number): string {
  return fetchMock.mock.calls[call][0] as string;
}

describe('GET requests', () => {
  it('includes credentials and never sends a CSRF token', async () => {
    fetchMock.mockResolvedValueOnce(
      mockResponse({ json: [{ guildId: '1', name: 'Alpha', role: 'OWNER' }] }),
    );

    const guilds = await api.listAuthorizedGuilds();

    expect(guilds).toHaveLength(1);
    expect(urlOf(0)).toBe('/api/v1/guilds');
    const init = initOf(0);
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('include');
    const headers = init.headers as Record<string, string>;
    expect(headers['X-CSRF-TOKEN']).toBeUndefined();
  });

  it('throws an unauthorized ApiError when /api/v1/me returns 401', async () => {
    fetchMock.mockResolvedValueOnce(mockResponse({ status: 401, json: { error: 'unauthorized' } }));

    const error = await api.getCurrentOperator().catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isUnauthorized).toBe(true);
  });

  it('lists guild channels without exposing internal ids', async () => {
    fetchMock.mockResolvedValueOnce(
      mockResponse({
        json: {
          channels: [
            {
              id: 'internal-id',
              discordChannelId: '123456789012345678',
              name: 'welcome',
              type: 'TEXT',
              displayName: '#welcome',
            },
          ],
        },
      }),
    );

    const channels = await api.listGuildChannels('42');

    expect(urlOf(0)).toBe('/api/v1/guilds/42/channels');
    expect(channels).toEqual([
      {
        discordChannelId: '123456789012345678',
        name: 'welcome',
        type: 'TEXT',
        displayName: '#welcome',
      },
    ]);
    expect((channels[0] as unknown as Record<string, unknown>).id).toBeUndefined();
  });

  it('requests guild audit log filters and ignores internal event fields', async () => {
    fetchMock.mockResolvedValueOnce(
      mockResponse({
        json: {
          guildId: '42',
          events: [
            {
              id: 'internal-event-id',
              registeredGuildId: 'internal-guild-id',
              operatorId: 'internal-operator-id',
              occurredAt: '2026-01-02T03:04:05Z',
              eventType: 'WELCOME_CONFIGURED',
              actorType: 'OPERATOR',
              summary: 'Welcome message configuration was updated.',
              targetType: 'WELCOME_MESSAGE',
              targetLabel: 'Welcome automation',
            },
          ],
        },
      }),
    );

    const auditLog = await api.getGuildAuditLog('42', {
      limit: 100,
      eventType: 'WELCOME_CONFIGURED',
      from: '2026-01-01T00:00:00Z',
      to: '2026-01-03T00:00:00Z',
    });

    expect(urlOf(0)).toBe(
      '/api/v1/guilds/42/audit-log?limit=100&eventType=WELCOME_CONFIGURED&from=2026-01-01T00%3A00%3A00Z&to=2026-01-03T00%3A00%3A00Z',
    );
    expect(auditLog).toEqual({
      guildId: '42',
      events: [
        {
          occurredAt: '2026-01-02T03:04:05Z',
          eventType: 'WELCOME_CONFIGURED',
          actorType: 'OPERATOR',
          summary: 'Welcome message configuration was updated.',
          targetType: 'WELCOME_MESSAGE',
          targetLabel: 'Welcome automation',
        },
      ],
    });
    expect((auditLog.events[0] as unknown as Record<string, unknown>).operatorId).toBeUndefined();
  });
});

describe('CSRF handling on state-changing requests', () => {
  it('fetches the CSRF token and sends it using the dynamic header name', async () => {
    fetchMock
      .mockResolvedValueOnce(CSRF_RESPONSE) // GET /api/v1/csrf
      .mockResolvedValueOnce(
        mockResponse({ json: { guildId: '9', name: 'G', timezone: 'UTC', locale: 'en-US', version: 1 } }),
      );

    await api.updateGuildSettings('9', { timezone: 'UTC', locale: 'en-US', expectedVersion: 0 });

    expect(urlOf(0)).toBe('/api/v1/csrf');
    expect(urlOf(1)).toBe('/api/v1/guilds/9/settings');

    const put = initOf(1);
    expect(put.method).toBe('PUT');
    expect(put.credentials).toBe('include');
    const headers = put.headers as Record<string, string>;
    expect(headers['X-CSRF-TOKEN']).toBe('token-123');
    expect(headers['Content-Type']).toBe('application/json');
  });

  it('caches the CSRF token across state-changing requests', async () => {
    fetchMock
      .mockResolvedValueOnce(CSRF_RESPONSE)
      .mockResolvedValueOnce(mockResponse({ status: 201, json: { guildId: '1', name: 'A', role: 'ADMIN' } }))
      .mockResolvedValueOnce(mockResponse({ status: 201, json: { guildId: '2', name: 'B', role: 'ADMIN' } }));

    await api.onboardGuild('1');
    await api.onboardGuild('2');

    const csrfCalls = fetchMock.mock.calls.filter((call) => call[0] === '/api/v1/csrf');
    expect(csrfCalls).toHaveLength(1);
  });

  it('refetches the CSRF token and retries once after a 403', async () => {
    fetchMock
      .mockResolvedValueOnce(CSRF_RESPONSE) // initial token
      .mockResolvedValueOnce(mockResponse({ status: 403, json: { error: 'forbidden' } })) // stale
      .mockResolvedValueOnce(
        mockResponse({ json: { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token: 'fresh' } }),
      ) // refreshed token
      .mockResolvedValueOnce(mockResponse({ status: 204 })); // retry success

    await api.revokeAccess('42');

    // Two CSRF fetches (initial + forced refresh), and the retry uses the fresh token.
    const csrfCalls = fetchMock.mock.calls.filter((call) => call[0] === '/api/v1/csrf');
    expect(csrfCalls).toHaveLength(2);
    const retryHeaders = initOf(3).headers as Record<string, string>;
    expect(retryHeaders['X-CSRF-TOKEN']).toBe('fresh');
  });

  it('surfaces a conflict ApiError on 409 settings updates', async () => {
    fetchMock
      .mockResolvedValueOnce(CSRF_RESPONSE)
      .mockResolvedValueOnce(mockResponse({ status: 409, json: { error: 'conflict' } }));

    const error = await api
      .updateGuildSettings('9', { timezone: 'UTC', locale: 'en-US', expectedVersion: 0 })
      .catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isConflict).toBe(true);
  });

  it('sends a target enabled value for idempotent member-message toggles', async () => {
    fetchMock
      .mockResolvedValueOnce(CSRF_RESPONSE)
      .mockResolvedValueOnce(mockResponse({
        json: {
          kind: 'WELCOME',
          configured: true,
          enabled: false,
          channelId: '123',
          title: 'Welcome',
          message: 'Hi',
          color: '#57F287',
          imageUrl: '',
          footer: '',
          includeBots: false,
          mentionMember: true,
          buttonLabel: '',
          buttonUrl: '',
        },
      }));

    await api.toggleMemberMessageConfig('9', 'welcome', false);

    expect(urlOf(1)).toBe('/api/v1/guilds/9/member-messages/welcome/toggle');
    expect(initOf(1).method).toBe('POST');
    expect(initOf(1).body).toBe(JSON.stringify({ enabled: false }));
  });
});

describe('getCsrfToken', () => {
  it('returns the parsed token and caches it', async () => {
    fetchMock.mockResolvedValueOnce(CSRF_RESPONSE);

    const first = await getCsrfToken();
    const second = await getCsrfToken();

    expect(first.token).toBe('token-123');
    expect(second).toEqual(first);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
