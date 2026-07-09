/**
 * Client-side view of the Guild OS backend DTOs.
 *
 * These interfaces mirror the documented backend responses but are intentionally tolerant: every
 * field the UI actually renders is validated defensively at parse time (see `parse.ts`) so a minor
 * backend field addition never breaks the dashboard, and an unexpected shape degrades to a safe
 * empty/partial render instead of a crash.
 */

/** `GET /api/v1/csrf` — values needed to echo the CSRF token on state-changing requests. */
export interface CsrfToken {
  headerName: string;
  parameterName: string;
  token: string;
}

/** `GET /api/v1/me` — the signed-in operator. Only safe Discord profile fields. */
export interface CurrentOperator {
  operatorId: string;
  discordUserId: string;
  username: string;
  displayName: string | null;
  avatarHash: string | null;
}

/** Guild OS role granted to an operator for a guild. */
export type GuildRole = 'OWNER' | 'ADMIN' | string;

/** Onboarding lifecycle status for an eligible guild. */
export type OnboardingStatus = 'AVAILABLE' | 'ONBOARDED' | 'REVOKED' | string;

/** `GET /api/v1/guilds` — a guild the operator is currently authorized to manage. */
export interface AuthorizedGuild {
  guildId: string;
  name: string | null;
  role: GuildRole | null;
}

/** `GET /api/v1/onboarding/guilds` — a guild the operator is eligible to onboard. */
export interface EligibleGuild {
  guildId: string;
  name: string | null;
  iconHash: string | null;
  discordRole: GuildRole | null;
  onboardingStatus: OnboardingStatus;
}

/** `GET|PUT /api/v1/guilds/{id}/settings` — shared per-guild settings resource. */
export interface GuildSettings {
  guildId: string;
  name: string | null;
  timezone: string;
  locale: string;
  version: number;
  updatedAt: string | null;
}

/** Body for `PUT /api/v1/guilds/{id}/settings`. */
export interface UpdateGuildSettingsRequest {
  timezone: string;
  locale: string;
  expectedVersion: number;
}

/** A single complete UTC-hour analytics bucket. */
export interface ActivityBucket {
  startedAt: string;
  messagesCreated: number;
  distinctMessagesEdited: number;
  messagesDeleted: number;
  humanMessages: number;
  botMessages: number;
  membersJoined: number;
  membersLeft: number;
  activeMembers: number;
  activeChannels: number;
}

/** Aggregate summary across the requested range. */
export interface ActivitySummary {
  messagesCreated: number;
  distinctMessagesEdited: number;
  messagesDeleted: number;
  humanMessages: number;
  botMessages: number;
  membersJoined: number;
  membersLeft: number;
  peakHourlyActiveMembers: number;
  peakHourlyActiveChannels: number;
}

/** `GET /api/v1/guilds/{id}/analytics/activity` response. */
export interface ActivityAnalytics {
  guildId: string;
  from: string;
  to: string;
  bucketTimezone: string;
  summary: ActivitySummary;
  buckets: ActivityBucket[];
}

/** Privacy-safe guild audit event. No internal ids, session data, or raw payloads are exposed. */
export interface GuildAuditEvent {
  occurredAt: string;
  eventType: string;
  actorType: string;
  summary: string;
  targetType: string | null;
  targetLabel: string | null;
}

/** `GET /api/v1/guilds/{id}/audit-log` response. */
export interface GuildAuditLog {
  guildId: string;
  events: GuildAuditEvent[];
}

/** Optional filters for the guild audit-log endpoint. */
export interface GuildAuditLogOptions {
  limit?: number;
  eventType?: string;
  from?: string;
  to?: string;
}

/** Active Discord text/announcement channel synced from the bot's Gateway cache. */
export interface GuildChannelSummary {
  discordChannelId: string;
  name: string;
  type: string;
  displayName: string;
}

/** Which member lifecycle message a configuration targets. */
export type MemberMessageKind = 'welcome' | 'goodbye';

/**
 * `GET|PUT|POST toggle` response for a welcome/goodbye configuration. Welcome-only fields
 * (`mentionMember`, `buttonLabel`, `buttonUrl`) are `null` for goodbye or when unconfigured.
 */
export interface MemberMessageConfig {
  kind: 'WELCOME' | 'GOODBYE' | string;
  configured: boolean;
  enabled: boolean;
  channelId: string;
  title: string;
  message: string;
  color: string;
  imageUrl: string;
  footer: string;
  includeBots: boolean;
  mentionMember: boolean | null;
  buttonLabel: string;
  buttonUrl: string;
}

/** Body for `PUT` / `POST .../preview` on a member-message configuration. */
export interface UpdateMemberMessageRequest {
  channelId: string;
  message: string;
  title?: string;
  color?: string;
  imageUrl?: string;
  footer?: string;
  includeBots?: boolean;
  mentionMember?: boolean;
  buttonLabel?: string;
  buttonUrl?: string;
}

/** Optional target body for `POST .../toggle`; omitted keeps the legacy flip behavior. */
export interface ToggleMemberMessageRequest {
  enabled?: boolean;
}

/** Body for creating a Discord member communication timeout. */
export interface CreateMemberTimeoutRequest {
  targetUserId: string;
  durationMinutes: number;
  reason?: string;
}

/** Safe response for a completed moderation action. No internal ids are exposed. */
export interface ModerationActionResponse {
  guildId: string;
  actionType: string;
  targetUserId: string;
  durationMinutes: number;
  status: string;
}

/** Privacy-safe moderation case. No database UUIDs, operator ids, Discord names, or raw reasons. */
export interface ModerationCase {
  publicCaseId: string;
  actionType: string;
  targetType: string;
  targetUserId: string;
  durationMinutes: number | null;
  status: string;
  summary: string;
  occurredAt: string;
}

/** `GET /api/v1/guilds/{id}/moderation/cases` response. */
export interface ModerationCasesResponse {
  guildId: string;
  cases: ModerationCase[];
}

/** Optional filters for moderation case history. */
export interface ModerationCasesOptions {
  limit?: number;
  actionType?: string;
  from?: string;
  to?: string;
}

/**
 * A single member returned by a live moderation search. These fields are transient selection
 * metadata resolved from Discord at request time; Guild OS does not persist them.
 */
export interface MemberSearchResultMember {
  userId: string;
  username: string | null;
  displayName: string | null;
  bot: boolean;
}

/** `GET /api/v1/guilds/{id}/moderation/members/search` — live, non-persisted member lookup. */
export interface MemberSearchResponse {
  guildId: string;
  query: string;
  limit: number;
  results: MemberSearchResultMember[];
}

/** `POST .../preview` rendered result. Never sent to Discord. */
export interface MemberMessagePreview {
  kind: 'WELCOME' | 'GOODBYE' | string;
  title: string;
  description: string;
  color: string;
  imageUrl: string | null;
  footer: string;
  memberCount: number;
  mentionMember: boolean;
  buttonLabel: string | null;
  buttonUrl: string | null;
}
