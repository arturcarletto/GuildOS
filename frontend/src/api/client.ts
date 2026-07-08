/**
 * Centralized, typed API client for the Guild OS backend.
 *
 * Design contract:
 * - Every request uses `credentials: "include"` so the server-side session cookie is sent.
 * - GET requests never send a CSRF token.
 * - POST / PUT / DELETE and `POST /logout` fetch the CSRF token from `GET /api/v1/csrf` and send it
 *   using the *dynamic* header name the backend returns (never a hardcoded header only).
 * - The CSRF token is cached in memory only (never localStorage/sessionStorage) and re-fetched when
 *   a state-changing request is rejected with 403 (stale/rotated token), then retried once.
 * - Non-OK responses become `ApiError` carrying the HTTP status and best-effort parsed body.
 * - OAuth/session tokens are never read, stored, or logged by the client.
 */

import {
  asArray,
  asBoolean,
  asNumber,
  asRequiredString,
  asString,
  isRecord,
} from './parse';
import type {
  ActivityAnalytics,
  ActivityBucket,
  ActivitySummary,
  AuthorizedGuild,
  CreateMemberTimeoutRequest,
  CsrfToken,
  CurrentOperator,
  EligibleGuild,
  GuildAuditEvent,
  GuildAuditLog,
  GuildAuditLogOptions,
  GuildChannelSummary,
  GuildSettings,
  MemberMessageConfig,
  MemberMessageKind,
  MemberMessagePreview,
  ModerationActionResponse,
  ToggleMemberMessageRequest,
  UpdateGuildSettingsRequest,
  UpdateMemberMessageRequest,
} from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown, message?: string) {
    super(message ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }

  /** True when the session is missing or expired; the UI treats this as signed-out. */
  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }
}

type StateChangingMethod = 'POST' | 'PUT' | 'DELETE';

const JSON_HEADERS: Readonly<Record<string, string>> = { Accept: 'application/json' };

/** In-memory CSRF token cache. Never persisted to web storage. */
let cachedCsrfToken: CsrfToken | null = null;

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  const body = await readBody(response);
  return new ApiError(response.status, body);
}

async function getJson<T>(path: string, parse: (value: unknown) => T): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    credentials: 'include',
    headers: JSON_HEADERS,
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return parse(await readBody(response));
}

/** Fetches (and caches) the current CSRF token. `forceRefresh` bypasses the cache. */
export async function getCsrfToken(forceRefresh = false): Promise<CsrfToken> {
  if (!forceRefresh && cachedCsrfToken) {
    return cachedCsrfToken;
  }
  const token = await getJson<CsrfToken>('/api/v1/csrf', parseCsrfToken);
  cachedCsrfToken = token;
  return token;
}

/** Drops the cached CSRF token (e.g. after logout or a 403 rejection). */
export function clearCsrfToken(): void {
  cachedCsrfToken = null;
}

async function sendStateChanging<T>(
  method: StateChangingMethod,
  path: string,
  parse: (value: unknown) => T,
  body?: unknown,
): Promise<T> {
  const attempt = async (token: CsrfToken): Promise<Response> => {
    const headers: Record<string, string> = {
      ...JSON_HEADERS,
      [token.headerName]: token.token,
    };
    const init: RequestInit = { method, credentials: 'include', headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(body);
    }
    return fetch(path, init);
  };

  let token = await getCsrfToken();
  let response = await attempt(token);

  // A 403 on a state-changing request usually means a stale or rotated CSRF token.
  // Invalidate, re-fetch once, and retry before surfacing the error.
  if (response.status === 403) {
    clearCsrfToken();
    token = await getCsrfToken(true);
    response = await attempt(token);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  return parse(await readBody(response));
}

// ---------------------------------------------------------------------------
// Defensive parsers (safe `unknown` -> typed).
// ---------------------------------------------------------------------------

function parseCsrfToken(value: unknown): CsrfToken {
  if (!isRecord(value)) {
    throw new ApiError(200, value, 'Malformed CSRF token response');
  }
  return {
    headerName: asRequiredString(value.headerName, 'X-CSRF-TOKEN'),
    parameterName: asRequiredString(value.parameterName, '_csrf'),
    token: asRequiredString(value.token),
  };
}

function parseCurrentOperator(value: unknown): CurrentOperator {
  if (!isRecord(value)) {
    throw new ApiError(200, value, 'Malformed operator response');
  }
  return {
    operatorId: asRequiredString(value.operatorId),
    discordUserId: asRequiredString(value.discordUserId),
    username: asRequiredString(value.username),
    displayName: asString(value.displayName),
    avatarHash: asString(value.avatarHash),
  };
}

function parseAuthorizedGuild(value: unknown): AuthorizedGuild | null {
  if (!isRecord(value)) {
    return null;
  }
  const guildId = asString(value.guildId);
  if (!guildId) {
    return null;
  }
  return {
    guildId,
    name: asString(value.name),
    role: asString(value.role),
  };
}

function parseEligibleGuild(value: unknown): EligibleGuild | null {
  if (!isRecord(value)) {
    return null;
  }
  const guildId = asString(value.guildId);
  if (!guildId) {
    return null;
  }
  return {
    guildId,
    name: asString(value.name),
    iconHash: asString(value.iconHash),
    discordRole: asString(value.discordRole),
    onboardingStatus: asRequiredString(value.onboardingStatus, 'AVAILABLE'),
  };
}

function parseGuildSettings(value: unknown): GuildSettings {
  if (!isRecord(value)) {
    throw new ApiError(200, value, 'Malformed settings response');
  }
  return {
    guildId: asRequiredString(value.guildId),
    name: asString(value.name),
    timezone: asRequiredString(value.timezone, 'UTC'),
    locale: asRequiredString(value.locale, 'en-US'),
    version: asNumber(value.version),
    updatedAt: asString(value.updatedAt),
  };
}

function parseActivityBucket(value: unknown): ActivityBucket | null {
  if (!isRecord(value)) {
    return null;
  }
  const startedAt = asString(value.startedAt);
  if (!startedAt) {
    return null;
  }
  return {
    startedAt,
    messagesCreated: asNumber(value.messagesCreated),
    distinctMessagesEdited: asNumber(value.distinctMessagesEdited),
    messagesDeleted: asNumber(value.messagesDeleted),
    humanMessages: asNumber(value.humanMessages),
    botMessages: asNumber(value.botMessages),
    membersJoined: asNumber(value.membersJoined),
    membersLeft: asNumber(value.membersLeft),
    activeMembers: asNumber(value.activeMembers),
    activeChannels: asNumber(value.activeChannels),
  };
}

function parseActivitySummary(value: unknown): ActivitySummary {
  const source = isRecord(value) ? value : {};
  return {
    messagesCreated: asNumber(source.messagesCreated),
    distinctMessagesEdited: asNumber(source.distinctMessagesEdited),
    messagesDeleted: asNumber(source.messagesDeleted),
    humanMessages: asNumber(source.humanMessages),
    botMessages: asNumber(source.botMessages),
    membersJoined: asNumber(source.membersJoined),
    membersLeft: asNumber(source.membersLeft),
    peakHourlyActiveMembers: asNumber(source.peakHourlyActiveMembers),
    peakHourlyActiveChannels: asNumber(source.peakHourlyActiveChannels),
  };
}

function parseActivityAnalytics(value: unknown): ActivityAnalytics {
  const source = isRecord(value) ? value : {};
  return {
    guildId: asRequiredString(source.guildId),
    from: asRequiredString(source.from),
    to: asRequiredString(source.to),
    bucketTimezone: asRequiredString(source.bucketTimezone, 'UTC'),
    summary: parseActivitySummary(source.summary),
    buckets: asArray(source.buckets, parseActivityBucket),
  };
}

function parseGuildChannelSummary(value: unknown): GuildChannelSummary | null {
  if (!isRecord(value)) {
    return null;
  }
  const discordChannelId = asString(value.discordChannelId);
  if (!discordChannelId) {
    return null;
  }
  const name = asRequiredString(value.name);
  return {
    discordChannelId,
    name,
    type: asRequiredString(value.type),
    displayName: asRequiredString(value.displayName, name ? `#${name}` : discordChannelId),
  };
}

function parseGuildChannels(value: unknown): GuildChannelSummary[] {
  const source = isRecord(value) ? value : {};
  return asArray(source.channels, parseGuildChannelSummary);
}

function parseGuildAuditEvent(value: unknown): GuildAuditEvent | null {
  if (!isRecord(value)) {
    return null;
  }
  const occurredAt = asString(value.occurredAt);
  const eventType = asString(value.eventType);
  const actorType = asString(value.actorType);
  if (!occurredAt || !eventType || !actorType) {
    return null;
  }
  return {
    occurredAt,
    eventType,
    actorType,
    summary: asRequiredString(value.summary),
    targetType: asString(value.targetType),
    targetLabel: asString(value.targetLabel),
  };
}

function parseGuildAuditLog(value: unknown): GuildAuditLog {
  const source = isRecord(value) ? value : {};
  return {
    guildId: asRequiredString(source.guildId),
    events: asArray(source.events, parseGuildAuditEvent),
  };
}

function parseMemberMessageConfig(value: unknown): MemberMessageConfig {
  const source = isRecord(value) ? value : {};
  return {
    kind: asRequiredString(source.kind, 'WELCOME'),
    configured: asBoolean(source.configured),
    enabled: asBoolean(source.enabled),
    channelId: asRequiredString(source.channelId),
    title: asRequiredString(source.title),
    message: asRequiredString(source.message),
    color: asRequiredString(source.color),
    imageUrl: asRequiredString(source.imageUrl),
    footer: asRequiredString(source.footer),
    includeBots: asBoolean(source.includeBots),
    mentionMember: typeof source.mentionMember === 'boolean' ? source.mentionMember : null,
    buttonLabel: asRequiredString(source.buttonLabel),
    buttonUrl: asRequiredString(source.buttonUrl),
  };
}

function parseMemberMessagePreview(value: unknown): MemberMessagePreview {
  const source = isRecord(value) ? value : {};
  return {
    kind: asRequiredString(source.kind, 'WELCOME'),
    title: asRequiredString(source.title),
    description: asRequiredString(source.description),
    color: asRequiredString(source.color),
    imageUrl: asString(source.imageUrl),
    footer: asRequiredString(source.footer),
    memberCount: asNumber(source.memberCount),
    mentionMember: asBoolean(source.mentionMember),
    buttonLabel: asString(source.buttonLabel),
    buttonUrl: asString(source.buttonUrl),
  };
}

function parseModerationActionResponse(value: unknown): ModerationActionResponse {
  const source = isRecord(value) ? value : {};
  return {
    guildId: asRequiredString(source.guildId),
    actionType: asRequiredString(source.actionType),
    targetUserId: asRequiredString(source.targetUserId),
    durationMinutes: asNumber(source.durationMinutes),
    status: asRequiredString(source.status),
  };
}

function memberMessageUrl(discordGuildId: string, kind: MemberMessageKind): string {
  return `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/member-messages/${kind}`;
}

function guildAuditLogUrl(discordGuildId: string, options: GuildAuditLogOptions = {}): string {
  const query = new URLSearchParams();
  if (options.limit !== undefined) {
    query.set('limit', String(options.limit));
  }
  if (options.eventType) {
    query.set('eventType', options.eventType);
  }
  if (options.from) {
    query.set('from', options.from);
  }
  if (options.to) {
    query.set('to', options.to);
  }
  const suffix = query.toString();
  return `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/audit-log${
    suffix ? `?${suffix}` : ''
  }`;
}

// ---------------------------------------------------------------------------
// Public API surface.
// ---------------------------------------------------------------------------

export const api = {
  getCurrentOperator(): Promise<CurrentOperator> {
    return getJson('/api/v1/me', parseCurrentOperator);
  },

  listAuthorizedGuilds(): Promise<AuthorizedGuild[]> {
    return getJson('/api/v1/guilds', (value) => asArray(value, parseAuthorizedGuild));
  },

  listEligibleGuilds(): Promise<EligibleGuild[]> {
    return getJson('/api/v1/onboarding/guilds', (value) => asArray(value, parseEligibleGuild));
  },

  onboardGuild(discordGuildId: string): Promise<AuthorizedGuild | null> {
    return sendStateChanging(
      'POST',
      `/api/v1/onboarding/guilds/${encodeURIComponent(discordGuildId)}`,
      parseAuthorizedGuild,
    );
  },

  revokeAccess(discordGuildId: string): Promise<void> {
    return sendStateChanging(
      'DELETE',
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/access`,
      () => undefined,
    );
  },

  getGuildSettings(discordGuildId: string): Promise<GuildSettings> {
    return getJson(
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/settings`,
      parseGuildSettings,
    );
  },

  updateGuildSettings(
    discordGuildId: string,
    request: UpdateGuildSettingsRequest,
  ): Promise<GuildSettings> {
    return sendStateChanging(
      'PUT',
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/settings`,
      parseGuildSettings,
      request,
    );
  },

  getActivityAnalytics(
    discordGuildId: string,
    fromIso: string,
    toIso: string,
  ): Promise<ActivityAnalytics> {
    const query = new URLSearchParams({ from: fromIso, to: toIso });
    return getJson(
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/analytics/activity?${query.toString()}`,
      parseActivityAnalytics,
    );
  },

  listGuildChannels(discordGuildId: string): Promise<GuildChannelSummary[]> {
    return getJson(
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/channels`,
      parseGuildChannels,
    );
  },

  getGuildAuditLog(
    discordGuildId: string,
    options: GuildAuditLogOptions = {},
  ): Promise<GuildAuditLog> {
    return getJson(guildAuditLogUrl(discordGuildId, options), parseGuildAuditLog);
  },

  getMemberMessageConfig(
    discordGuildId: string,
    kind: MemberMessageKind,
  ): Promise<MemberMessageConfig> {
    return getJson(memberMessageUrl(discordGuildId, kind), parseMemberMessageConfig);
  },

  updateMemberMessageConfig(
    discordGuildId: string,
    kind: MemberMessageKind,
    request: UpdateMemberMessageRequest,
  ): Promise<MemberMessageConfig> {
    return sendStateChanging(
      'PUT',
      memberMessageUrl(discordGuildId, kind),
      parseMemberMessageConfig,
      request,
    );
  },

  toggleMemberMessageConfig(
    discordGuildId: string,
    kind: MemberMessageKind,
    enabled?: boolean,
  ): Promise<MemberMessageConfig> {
    const request: ToggleMemberMessageRequest | undefined =
      enabled === undefined ? undefined : { enabled };
    return sendStateChanging(
      'POST',
      `${memberMessageUrl(discordGuildId, kind)}/toggle`,
      parseMemberMessageConfig,
      request,
    );
  },

  previewMemberMessageConfig(
    discordGuildId: string,
    kind: MemberMessageKind,
    request: UpdateMemberMessageRequest,
  ): Promise<MemberMessagePreview> {
    return sendStateChanging(
      'POST',
      `${memberMessageUrl(discordGuildId, kind)}/preview`,
      parseMemberMessagePreview,
      request,
    );
  },

  createMemberTimeout(
    discordGuildId: string,
    request: CreateMemberTimeoutRequest,
  ): Promise<ModerationActionResponse> {
    return sendStateChanging(
      'POST',
      `/api/v1/guilds/${encodeURIComponent(discordGuildId)}/moderation/timeout`,
      parseModerationActionResponse,
      request,
    );
  },

  async logout(): Promise<void> {
    await sendStateChanging('POST', '/logout', () => undefined);
    clearCsrfToken();
  },
};
