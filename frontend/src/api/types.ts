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
