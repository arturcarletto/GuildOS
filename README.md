# Guild OS

Guild OS is a platform for managing, automating, and analyzing online communities. It is evolving from a Discord-first backend toward multi-platform community management: a Guild OS core (activity ingestion, analytics, operator/dashboard APIs, onboarding, settings, audit logging, and small platform-neutral abstractions) with pluggable platform adapters. Discord is the first complete adapter; Telegram is an experimental, disabled-by-default proof-of-concept adapter. This repository currently contains the production-oriented foundation for its backend, an optional Discord Gateway connection, a persistent registry of connected guilds, optional Discord OAuth2 login for human operators, guild onboarding that authorizes operators to manage specific guilds, authorized persistent guild settings, synced Discord channel metadata, a privacy-safe guild audit log, the guild-scoped `/status` command, Discord administration and delivery of welcome/goodbye messages, durable Discord activity ingestion, authorized hourly activity analytics, a first operator dashboard frontend that consumes the existing authenticated APIs, and a minimal Telegram adapter that answers a `/ping` command.

## Project status

The project is at the initial bootstrap stage. It provides a runnable Spring Boot service, PostgreSQL persistence foundation, Flyway migrations, real-database integration tests, local Docker Compose infrastructure, backend and frontend CI, a monitored Discord Gateway connection, a persistent guild registry, server-side operator authentication through Discord OAuth2, operator-to-guild authorization, persistent per-guild timezone and locale settings, synced Discord text/announcement channel metadata, a privacy-safe guild-scoped audit log, an ephemeral read-only Discord status command, persistent welcome/goodbye configuration and delivery, durable privacy-conscious member/message activity ingestion, asynchronous PostgreSQL-backed processing, an authorized hourly analytics API, a first React operator dashboard frontend for sign-in, onboarding, settings, analytics, audit-log review, and member-message automation, a small platform-neutral abstraction layer, and an experimental Telegram adapter proof of concept. Bot Gateway, human OAuth, and Telegram integrations are independently disabled by default. Discord remains the first complete adapter; the Telegram adapter is an early proof of concept that only answers `/ping` and does not yet onboard chats, authenticate operators, persist activity, or deliver welcome/goodbye messages. The frontend is an early foundation: real-time dashboards, moderation, AI features, billing, retention automation, and advanced analytics visualization are not implemented yet.

## Technology stack

- Java 21
- Spring Boot 4.1.0
- Spring MVC, Spring Security OAuth2 Client, Spring Data JPA, Jakarta Bean Validation, and Actuator
- PostgreSQL 17
- Flyway
- JDA 6.4.2
- Maven Wrapper
- JUnit and Testcontainers
- Docker Compose
- GitHub Actions

## Repository structure

```text
GuildOS/
|-- .github/workflows/
|   |-- backend-ci.yml
|   `-- frontend-ci.yml
|-- backend/
|   |-- .mvn/wrapper/
|   |-- src/main/
|   |-- src/test/
|   |-- mvnw
|   |-- mvnw.cmd
|   `-- pom.xml
|-- frontend/
|   |-- public/
|   |-- src/
|   |   |-- api/          # typed API client, DTO types, CSRF/session handling
|   |   |-- auth/         # session/auth React context
|   |   |-- components/   # app shell, shared UI, formatting helpers
|   |   |-- hooks/        # async data-loading hook
|   |   |-- pages/        # landing, dashboard, guilds, guild detail tabs
|   |   |-- styles/       # design tokens and layout CSS
|   |   `-- test/         # Vitest setup and render helpers
|   |-- index.html
|   |-- package.json
|   |-- vite.config.ts
|   |-- eslint.config.js
|   `-- tsconfig.json
|-- docs/
|   |-- adr/0001-modular-monolith.md
|   |-- adr/0002-platform-adapter-architecture.md
|   `-- architecture.md
|-- .editorconfig
|-- .env.example
|-- .gitattributes
|-- .gitignore
|-- compose.yaml
`-- README.md
```

## Prerequisites

- JDK 21 available on `PATH`
- Docker Desktop with Docker Compose enabled
- Bash-compatible shell, such as Linux, macOS, WSL, or Git Bash
- Node.js LTS (Node 22 recommended) and npm, for the operator dashboard frontend

Maven does not need to be installed; the repository includes the Maven Wrapper. Node.js and npm are only required to run or build the `frontend/` dashboard.

## Start locally with Bash

From the repository root, optionally create a local Compose environment file:

```bash
cp .env.example .env
```

The committed values are non-sensitive local-development defaults. Change the values in `.env` if they conflict with your environment. The root `.env` file is read by Docker Compose only; a Spring Boot process started directly uses the `local` profile and variables from its shell environment.

Start PostgreSQL and wait until its health status is `healthy`:

```bash
docker compose up -d postgres
docker compose ps
```

Run the backend with the `local` profile:

```bash
cd backend
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile connects to `jdbc:postgresql://localhost:5432/guildos` with the documented local-only `guildos_app` / `guildos_local` credentials. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can override them.

In another terminal, verify application health:

```bash
curl http://localhost:8080/actuator/health
```

The response should report `status` as `UP`. Actuator exposes only `health` and `info`, and health details are not public.

Stop local infrastructure from the repository root:

```bash
docker compose down
```

Add `-v` only when you intentionally want to delete the local PostgreSQL volume.

## Connect to the Discord Gateway

The Discord Gateway integration is disabled by default, so the backend starts without a Discord token. To enable it temporarily, create a bot in the Discord Developer Portal, install it in a test server, and set these variables in the shell session that will start Spring Boot:

```bash
export GUILDOS_DISCORD_ENABLED=true
export DISCORD_BOT_TOKEN=your-local-token
```

Never commit or share a Discord bot token. Do not put a real token in `.env.example` or another tracked file.

With PostgreSQL running, start the application from the repository root:

```bash
cd backend
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Startup waits until JDA reports the initial Gateway connection as ready. The integration then synchronizes the guilds currently available to JDA into PostgreSQL. Guild join events create or reconnect registry entries, and guild leave events mark entries disconnected without deleting their history.

The integration enables exactly two Gateway intents: privileged `GUILD_MEMBERS` for member lifecycle messages and member activity, and standard `GUILD_MESSAGES` for message activity metadata. It never enables `MESSAGE_CONTENT` and does not request presences. Guild onboarding, operator authorization, persistent timezone/locale settings, and authorized analytics are available through the authenticated APIs described below. Operator authentication is provided separately through Discord OAuth2.

Check the existing Actuator health endpoint from another terminal:

```bash
curl http://localhost:8080/actuator/health
```

When enabled, the `discord` health contributor is `UP` only while JDA is connected. The current safe health policy exposes the aggregate status but does not expose component details publicly.

After stopping the application, remove the credentials from the current shell session:

```bash
unset GUILDOS_DISCORD_ENABLED
unset DISCORD_BOT_TOKEN
```

## Use the Discord status command

`/status` is the first implemented Discord command. It is registered as a guild-scoped command for every currently connected guild on Gateway ready and for newly joined guilds. It is available to normal guild members, has no options, does not require Administrator or Manage Server permission, and responds ephemerally so status checks do not clutter a channel. It is not available in direct messages.

Install the bot with both the bot and application-command scopes, requesting no Discord permissions for this read-only command:

```text
https://discord.com/oauth2/authorize?client_id=<APPLICATION_ID>&scope=bot%20applications.commands&permissions=0
```

Bot installation and human operator login are separate flows. The installation URL adds this Discord application and its commands to a server. `/oauth2/authorization/discord` signs a human operator into Guild OS so they can onboard and configure eligible guilds. Do not use the human OAuth callback URL (`/login/oauth2/code/discord`) as the bot installation redirect.

For an onboarded guild, `/status` reports the safe guild name, connected state, active onboarding state, timezone, locale, and settings version. If settings have never been materialized through the authorized HTTP API, the command reports `UTC`, `en-US`, and version `0` without creating a settings row. A connected guild that has not been onboarded receives a safe explanation that an eligible operator must sign in and complete onboarding. The command never exposes operator records, internal ids, Discord permissions, OAuth credentials, sessions, or bot credentials.

To verify locally:

1. Start PostgreSQL and configure the Discord Gateway and human OAuth environment variables described in this README.
2. Install the bot in a test guild with the URL template above and start Guild OS.
3. Sign in through `http://localhost:8080/oauth2/authorization/discord`, onboard the test guild, and configure its timezone and locale through the existing settings API.
4. Run `/status` in the guild and confirm the ephemeral response contains the configured values.
5. Install the bot in a second, non-onboarded guild and confirm the command returns the onboarding-required response.
6. Restart Guild OS and confirm guild-scoped command reconciliation remains idempotent and the command still responds.

Command limitations are deliberate: `/status` is not a moderation, automation, dashboard, AI, localization, global command rollout, or frontend feature.

## Welcome and goodbye messages from Discord

The authoritative guild command catalog registers two parallel, consistent commands — `/welcome` and `/goodbye` — each with the same four subcommands:

- `/welcome status` / `/goodbye status` — show the current configuration as a polished ephemeral embed (channel, enabled state, title, message, footer, accent color, and whether an image, button, mention or bot inclusion is configured).
- `/welcome configure` / `/goodbye configure` — create or update the configuration. The first configuration is enabled by default; editing an already-disabled configuration keeps it disabled.
- `/welcome preview` / `/goodbye preview` — render the exact public message (embed, thumbnail, image, footer, timestamp and button) as an ephemeral response using the invoking member as the sample. Nothing is sent to the configured channel and nobody is notified.
- `/welcome toggle` / `/goodbye toggle` — switch an existing configuration between enabled and disabled, preserving all channel and appearance values. It replaces the old `/welcome disable` subcommand and never creates a configuration; run `configure` first.

Both commands default to members with Manage Server, and Guild OS independently rechecks the invoking member's effective Manage Server permission on every interaction (a Manage Server user is not necessarily a Discord Administrator). The guild must be currently connected and have at least one active Guild OS onboarding authorization. All administrative responses are ephemeral embeds with every allowed mention disabled, and they never expose the internal optimistic-locking version, database identifiers, operator data, OAuth data, or tokens.

### Configure options

`configure` requires `channel` and `message` (the embed description). Optional values are preserved when omitted on a later edit:

- `title` — embed title.
- `color` — accent color as a hex value like `#57F287` (Discord green) or `#ED4245` (Discord red).
- `image` — banner image HTTPS URL.
- `footer` — embed footer.
- `include-bots` — also announce bot accounts (default off).

Welcome adds three welcome-only options: `mention-member` (ping the joining member), `button-label` and `button-url` (a single link button; both are required together). Goodbye never mentions the departing user and has no button.

The configure channel must be a standard guild text or announcement channel in the invoking guild, and the bot must have View Channel, Send Messages and **Embed Links** in it; Administrator is never requested. A deleted or newly inaccessible saved channel is reported safely by status and preview without deleting the persisted configuration. Image and button URLs must be HTTPS; Guild OS never downloads or inspects them — Discord resolves them when rendering.

### Template placeholders

Titles, descriptions, footers and button labels may be static or use these deterministic, non-recursive placeholders:

- `{member}` — safe display name;
- `{username}` — safe Discord username;
- `{server}` — safe guild name;
- `{memberCount}` — current member count;
- `{mention}` — a real mention of the member (welcome only; rejected in goodbye).

Unknown placeholders, malformed braces, `@everyone`, `@here`, and raw Discord user, role or channel mention syntax are rejected. A welcome mention is generated by the adapter from the joining user's id and the message allows mentioning only that one user; goodbye messages mention nobody. Each field is validated at configuration time against worst-case rendered lengths so an accepted template always fits Discord's embed limits.

### Real delivery, the GUILD_MEMBERS intent, and neutral goodbye wording

On a real member join, Guild OS delivers the welcome embed; on a member removal, it delivers the goodbye embed. This requires the privileged **`GUILD_MEMBERS`** Gateway intent, which Guild OS enables automatically when the Discord integration is enabled. You must also enable **Server Members Intent** for the application in the Discord Developer Portal (Bot -> Privileged Gateway Intents). `GUILD_MESSAGES` is also enabled for activity metadata, but `MESSAGE_CONTENT` and presence are never requested.

The member removal event covers voluntary leaves, kicks and bans alike, so goodbye copy uses neutral wording (for example "A member has left" / "{member} is no longer in {server}") and never claims the member left voluntarily. Guild OS does not read the audit log or request View Audit Log.

Delivery reads the active configuration, then sends asynchronously entirely outside any database transaction. If the channel is missing or the bot lacks View Channel, Send Messages or Embed Links, delivery is skipped safely without disabling or deleting the configuration and without any database write. Delivery failures never crash the Gateway listener and never log message content, templates or URLs. Outcomes are recorded on a bounded Micrometer counter `guildos.discord.member_message.delivery` tagged only by `kind` (`welcome`/`goodbye`) and `outcome` (`sent`, `disabled`, `not_configured`, `bot_ignored`, `channel_unavailable`, `permission_denied`, `send_failed`); no guild, channel or user identifiers are used as metric tags.

Install the bot with the `bot` and `applications.commands` scopes, granting it View Channel, Send Messages and Embed Links in the intended channels:

```text
https://discord.com/oauth2/authorize?client_id=<APPLICATION_ID>&scope=bot%20applications.commands
```

### Verify the feature manually in a test guild

1. Start PostgreSQL, enable the Discord Gateway and human Discord OAuth, and enable **Server Members Intent** in the Discord Developer Portal, then start Guild OS.
2. Install the bot with the `bot` and `applications.commands` scopes and grant it View Channel, Send Messages and Embed Links in the test channels.
3. Complete browser OAuth onboarding for the connected test guild.
4. Confirm command reconciliation exposes `/status`, `/welcome` (`status`, `configure`, `preview`, `toggle`) and `/goodbye` (`status`, `configure`, `preview`, `toggle`), and that `/welcome disable` no longer exists.
5. Configure welcome, for example:

   ```text
   /welcome configure channel:#welcome message:Hey **{member}**, welcome to **{server}**! You are member **#{memberCount}**.
     title:Welcome to {server}! color:#57F287 footer:Welcome • {server}
     mention-member:true button-label:Read the rules button-url:https://example.com/rules
   ```

6. Run `/welcome preview` and confirm the complete ephemeral embed and button, then `/welcome status` and confirm no internal version is shown. Run `/welcome toggle` twice and confirm the state flips correctly.
7. Join with a test account and confirm exactly one polished welcome message appears in `#welcome`.
8. Configure goodbye (`/goodbye configure channel:#goodbye message:We’re saying goodbye to **{member}**. …`), run `/goodbye preview`, remove the test account, and confirm one neutral goodbye embed appears.
9. Remove the bot's Embed Links permission and confirm delivery is skipped safely; delete a configured channel and confirm status warns without deleting the persisted row.
10. Restart Guild OS and confirm both configurations persist.

## Durable activity ingestion and hourly analytics

Guild OS records privacy-conscious Discord activity metadata for registered guilds that currently have at least one active Guild OS onboarding authorization. Unknown guilds, non-onboarded guilds, direct messages, and unsupported contexts are ignored safely and do not create inbox rows. Historical analytics remain readable while the bot is disconnected as long as the authenticated operator still has active Guild OS authorization for the guild.

The Discord adapter listens for member joins, member removals, message creates, message edits, message deletes, and bulk message deletes. Bulk deletes are represented as one `MESSAGE_DELETED` event per deleted message id. The adapter translates JDA events into platform-neutral commands owned by `guildactivity`; no JDA event, channel, member, user, or message type crosses into that package.

The stored metadata is limited to:

- source event id;
- event type;
- internal registered guild id;
- subject Discord id (member id or message id);
- channel id for message events;
- actor user id and bot flag only when Discord safely provides a normal actor;
- occurrence time, receive time, schema version, processing state, retry counters, and bounded failure category.

Guild OS never reads, stores, hashes, serializes, logs, or exposes message text, embed content, attachment names or URLs, component custom ids, sticker names, poll answers, usernames, display names, guild names, or channel names as activity payload data. The `guild_activity_events` table has no JSON, TEXT, BYTEA, content, or payload column. Metrics use only bounded low-cardinality tags such as event type and outcome; no guild, channel, message, user, URL, or exception message is used as a metric tag.

PostgreSQL is the durable queue for this stage. Gateway callbacks perform one short transactional insert into `guild_os.guild_activity_events` using `INSERT ... ON CONFLICT (source_event_id) DO NOTHING`, so duplicate source events are successful no-ops. A scheduled processor claims bounded batches with `FOR UPDATE SKIP LOCKED`, marks rows `PROCESSING`, increments `attempt_count`, and then processes each row in its own transaction. Projection updates and marking an inbox row `PROCESSED` commit atomically. Failures roll back the projection transaction, then a separate short transaction stores only a bounded failure category and either reschedules the row with capped exponential backoff or marks it `DEAD` after the maximum attempts. Stale `PROCESSING` locks are reclaimable only when the claim query detects an expired lock, so a worker crash does not permanently block the row. A lost claim is recorded separately from retries and dead-lettering.

Hourly analytics are stored as complete UTC-hour buckets in `guild_os.guild_activity_hourly`. Counters include created messages, distinct edited messages, deleted messages, member joins, member leaves, human messages, bot messages, active members, and active channels. Companion uniqueness tables keep hourly active member/channel counts idempotent under retries and concurrent workers. This is at-least-once processing with idempotent projection; it is not claimed to be exactly once. Member-left source ids are best-effort deduplicated from the captured event time plus guild/user ids because Discord does not provide a native leave event id.

Read activity analytics with an authenticated session:

```text
GET /api/v1/guilds/{discordGuildId}/analytics/activity?from=2026-07-03T00:00:00Z&to=2026-07-04T00:00:00Z
```

`from` is inclusive, `to` is exclusive, and both must be ISO-8601 instants exactly aligned to UTC hour boundaries, such as `2026-07-03T10:00:00Z`. Values like `10:30:00Z`, `10:00:01Z`, or nanosecond offsets are rejected with JSON `400` rather than rounded or truncated. The endpoint returns complete hourly buckets only and has no partial-hour precision. The maximum range is 31 days. Missing guilds, missing access, and revoked access all return the same non-enumerating JSON `404`. A valid request with no data returns zero summary values and an empty bucket array. Responses never expose internal guild UUIDs, inbox event ids, processing status, retry/failure details, raw user/channel/message ids, operator data, OAuth data, sessions, or tokens.

Example response shape:

```json
{
  "guildId": "100000000000000123",
  "from": "2026-07-03T00:00:00Z",
  "to": "2026-07-04T00:00:00Z",
  "bucketTimezone": "UTC",
  "summary": {
    "messagesCreated": 0,
    "distinctMessagesEdited": 0,
    "messagesDeleted": 0,
    "humanMessages": 0,
    "botMessages": 0,
    "membersJoined": 0,
    "membersLeft": 0,
    "peakHourlyActiveMembers": 0,
    "peakHourlyActiveChannels": 0
  },
  "buckets": []
}
```

Activity processor configuration uses bounded, non-secret properties:

```bash
export GUILDOS_ACTIVITY_PROCESSING_ENABLED=true
export GUILDOS_ACTIVITY_PROCESSING_FIXED_DELAY_MS=10000
export GUILDOS_ACTIVITY_PROCESSING_BATCH_SIZE=100
export GUILDOS_ACTIVITY_PROCESSING_MAX_ATTEMPTS=5
export GUILDOS_ACTIVITY_PROCESSING_INITIAL_RETRY_DELAY_MS=1000
export GUILDOS_ACTIVITY_PROCESSING_MAX_RETRY_DELAY_MS=60000
export GUILDOS_ACTIVITY_PROCESSING_STALE_LOCK_TIMEOUT_MS=300000
```

Processing metrics are emitted on `guildos.activity.processing` with bounded `event_type` and `outcome` tags only. Outcomes are `processed`, `retry_scheduled`, `dead`, `claim_lost`, and `stale_reclaimed`; `stale_reclaimed` is emitted only when a claim actually recovered a previously expired `PROCESSING` row.

Manual verification in a test guild:

1. Start PostgreSQL, enable Discord Gateway and human OAuth, enable **Server Members Intent**, and start Guild OS.
2. Install the bot in a test guild and complete browser onboarding.
3. Send, edit, and delete a message in a normal guild channel; join and remove a test member if available.
4. Query the analytics endpoint for a UTC range covering those events and confirm ordered hourly buckets and summary counters.
5. Confirm no `MESSAGE_CONTENT` intent is enabled in the Discord Developer Portal or application logs.
6. Re-run the same event source through tests or local replay and confirm duplicate source ids do not increment counters again.

RabbitMQ, Kafka, Redis, a separate analytics database, real-time dashboards, moderation automation, message-content analysis, and distributed deployment are intentionally deferred. The current implementation keeps the durable queue and projection in PostgreSQL inside the modular monolith until measured scale or ownership pressure justifies additional infrastructure.

## Authenticate operators with Discord OAuth2

Human operator login is separate from the JDA bot connection and is disabled by default. The flow requests only the `identify` and `guilds` scopes; it never requests bot-installation scopes. In the Discord Developer Portal, add this OAuth2 redirect URI:

```text
http://localhost:8080/login/oauth2/code/discord
```

Set the OAuth client credentials only in the shell session that starts Spring Boot:

```bash
export GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED=true
export DISCORD_OAUTH_CLIENT_ID=your-client-id
export DISCORD_OAUTH_CLIENT_SECRET=your-client-secret
export DISCORD_OAUTH_REDIRECT_URI=http://localhost:8080/login/oauth2/code/discord
```

Never commit or share the client secret. With PostgreSQL running, start the backend with the local profile as described above, then open this explicit login entry point in a browser:

```text
http://localhost:8080/oauth2/authorization/discord
```

After Discord authentication, the backend redirects to a configurable success URL that defaults to `GET /api/v1/me`. That protected endpoint returns only the local operator ID and safe Discord profile fields. Discord access and refresh tokens are never stored in Guild OS domain tables, and OAuth tokens and client secrets are never exposed by any API. Authentication establishes operator identity; onboarding a guild to authorize its management is described in the next section.

The post-login redirect is controlled by `guildos.identity.discord-oauth.success-redirect-uri` (environment variable `DISCORD_OAUTH_SUCCESS_REDIRECT_URI`). The default profile keeps the original `/api/v1/me` behavior, while the `local` profile points it at the operator dashboard on the Vite dev server (`http://localhost:5173/dashboard`) so a browser lands back in the dashboard after signing in. Set `DISCORD_OAUTH_SUCCESS_REDIRECT_URI` to override it for any environment:

```bash
export DISCORD_OAUTH_SUCCESS_REDIRECT_URI=http://localhost:5173/dashboard
```

Spring Security uses a server-side HTTP session. Logout uses `POST /logout` and requires the active CSRF token; successful logout returns HTTP 204. The current in-memory, single-instance session strategy must be revisited before horizontal scaling rather than adding distributed session storage prematurely.

After local testing, remove the OAuth variables from the current shell session:

```bash
unset GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED
unset DISCORD_OAUTH_CLIENT_ID
unset DISCORD_OAUTH_CLIENT_SECRET
unset DISCORD_OAUTH_REDIRECT_URI
```

## Onboard guilds and authorize operators

Because the human OAuth flow requests the `guilds` scope, Guild OS can read the operator's Discord guild list and apply a per-guild authorization boundary that is separate from the bot's Gateway connection. Two independent conditions gate onboarding:

- **OAuth eligibility** comes from Discord: an operator is eligible for a guild when Discord reports that they own it, or that they hold the `ADMINISTRATOR` or `MANAGE_GUILD` permission. Eligibility is always verified server-side from live Discord data using the correct permission bits; a browser-supplied claim is never trusted.
- **Bot presence** comes from the guild registry: a guild can only be onboarded while the Guild OS bot is currently connected to it.

A guild is onboardable only when both hold. Onboarding persists an operator-to-guild authorization with a Guild OS role: a Discord guild owner maps to `OWNER`; the `ADMINISTRATOR` or `MANAGE_GUILD` permission maps to `ADMIN`. The relationship is unique per operator and guild. Revocation is soft — it records a revocation time and preserves history — is idempotent, and can be reactivated by onboarding again, which preserves the original grant time.

All endpoints require an authenticated session, and state-changing requests require the active CSRF token:

- `GET /api/v1/onboarding/guilds` — lists the operator's eligible guilds where the bot is connected, each with an `onboardingStatus` of `AVAILABLE`, `ONBOARDED`, or `REVOKED`.
- `POST /api/v1/onboarding/guilds/{discordGuildId}` — onboards a guild after independently re-verifying eligibility and bot connection; returns `201 Created` for a newly created authorization, or `200 OK` when an existing authorization is reactivated, has its role updated, or is already current.
- `GET /api/v1/guilds` — lists only the current operator's active authorizations.
- `DELETE /api/v1/guilds/{discordGuildId}/access` — revokes only the current operator's authorization and returns `204 No Content`; it never disconnects the bot, deletes the guild registry entry, or affects another operator.

An operator can never see or revoke another operator's authorization. API responses expose only safe fields — never permission bitsets, OAuth access or refresh tokens, session identifiers, or client secrets. Discord access and refresh tokens are never persisted in Guild OS domain tables; the access token is read from Spring Security's authorized-client store only for the duration of a request. Error responses are consistent JSON: `401` when unauthenticated or when Discord authorization must be renewed, `403` when the operator lacks Discord eligibility, `404` when the guild is unknown or the bot is disconnected, `400` for an invalid guild id, and `502` or `503` when Discord is unavailable or returns an unusable response.

As with the login session, this authorized-client and session strategy is single-instance and must be revisited before horizontal scaling. To try it locally, enable Discord OAuth as above (the `guilds` scope is requested automatically), sign in through `http://localhost:8080/oauth2/authorization/discord`, then call the endpoints above from the authenticated browser session.

## Manage authorized guild settings

An authenticated operator with an active `OWNER` or `ADMIN` Guild OS authorization can manage one shared settings resource for that guild. Authorization comes only from the authenticated server-side principal and the persisted operator-to-guild relationship; requests cannot supply an operator id, role, internal guild id, or Discord permission claim.

Read settings with:

```text
GET /api/v1/guilds/{discordGuildId}/settings
```

The first authorized access safely materializes the defaults and returns them:

```json
{
  "guildId": "100000000000000123",
  "name": "Example Guild",
  "timezone": "UTC",
  "locale": "en-US",
  "version": 0,
  "updatedAt": "2026-07-03T00:00:00Z"
}
```

Replace the supported settings with a CSRF-protected request:

```text
PUT /api/v1/guilds/{discordGuildId}/settings
```

```json
{
  "timezone": "America/Sao_Paulo",
  "locale": "pt-BR",
  "expectedVersion": 0
}
```

The timezone must be a valid Java/IANA zone id and the locale must be a well-formed BCP 47 language tag. Both are returned in canonical form. A real change increments `version`; a canonical no-op preserves both the version and `updatedAt`. A stale `expectedVersion` returns HTTP `409` with `{"error":"conflict"}` and changes nothing.

`GET` needs no CSRF token. `PUT` requires the token from `GET /api/v1/csrf` and returns `403` with `{"error":"forbidden"}` when it is missing. Unknown guilds, missing access, and revoked access all return the same JSON `404`. Settings remain readable and writable while the bot is temporarily disconnected as long as the operator's persisted authorization remains active. Two authorized operators for the same guild see the same settings resource.

The settings API does not call Discord and never exposes internal persistence ids, operator ids, OAuth tokens, permission bitsets, session identifiers, or client secrets. It does not implement moderation or welcome-message execution, additional guild policy fields, or a frontend.

### CSRF tokens for API clients

CSRF protection stays enabled, so every state-changing request — `POST /api/v1/onboarding/guilds/{discordGuildId}`, `DELETE /api/v1/guilds/{discordGuildId}/access`, `PUT /api/v1/guilds/{discordGuildId}/settings`, and `POST /logout` — must carry a valid CSRF token. An authenticated client obtains the current token from a dedicated endpoint:

```text
GET /api/v1/csrf
```

It requires an authenticated session (it stays under the `/api/**` policy) and returns only the values needed to send the token back:

```json
{
  "headerName": "X-CSRF-TOKEN",
  "parameterName": "_csrf",
  "token": "<opaque-token>"
}
```

Send the returned `token` on each state-changing request using the returned `headerName` (for example `X-CSRF-TOKEN: <token>`), keeping the same session cookie so the server can match it. A browser calls `GET /api/v1/csrf` after login, then includes the returned header on subsequent state-changing `POST`, `PUT`, and `DELETE` requests, including `POST /logout`; a successful logout returns `204 No Content`. The token is never exposed through any other endpoint, DTO, or log.

## Operator dashboard frontend

The `frontend/` directory contains the first operator dashboard: a React + TypeScript single-page application built with Vite. It is a separate application in the monorepo and does not change or replace the backend. It signs an operator in through the existing Discord OAuth flow, then reads and writes only through the authenticated `/api/**` endpoints using the server-side session cookie and CSRF token — it never stores OAuth or session tokens in the browser.

### Prerequisites

- Node.js LTS (Node 22 recommended) and npm.

### Run the backend

Start the backend first so the frontend has an API to call. With PostgreSQL running and Discord OAuth enabled (see the OAuth section above), run from the repository root:

```bash
cd backend
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The backend listens on `http://localhost:8080`.

### Run the frontend

In a second terminal, from the repository root:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173`. It proxies the `/api`, `/oauth2`, `/login`, and `/logout` paths to the backend at `http://localhost:8080`, so browser requests stay same-origin during development and no CORS changes or Spring Security relaxation are needed. Set `GUILDOS_BACKEND_ORIGIN` before `npm run dev` to point the proxy at a different backend origin.

### Session, CSRF, and local OAuth flow

The frontend relies entirely on the backend for authentication and authorization:

- Sign-in starts from the frontend "Sign in with Discord" button, which navigates to `/oauth2/authorization/discord`.
- The backend performs the Discord OAuth handshake and establishes a server-side session; the browser never receives Discord tokens.
- The frontend then calls the authenticated `/api/**` endpoints with `credentials: "include"` so the session cookie is sent.
- `GET` requests need no CSRF token. For `POST`, `PUT`, `DELETE`, and `POST /logout`, the client fetches the token from `GET /api/v1/csrf`, caches it in memory, and sends it using the returned `headerName`. A 403 triggers one automatic token refresh and retry.

Because the registered Discord redirect URI points at `http://localhost:8080`, the OAuth handshake completes on the backend origin. Under the `local` profile the backend then redirects the browser to `http://localhost:5173/dashboard` (via `guildos.identity.discord-oauth.success-redirect-uri`, overridable with `DISCORD_OAUTH_SUCCESS_REDIRECT_URI`), so operators land back in the dashboard signed in through the shared `localhost` session cookie. The default profile keeps the original `/api/v1/me` behavior. Serving the built assets from Spring Boot in production remains a deliberate follow-up rather than a security shortcut.

### Frontend commands

```bash
cd frontend
npm install        # install dependencies and create/update package-lock.json
npm run dev        # start the Vite dev server on port 5173
npm run test       # run the Vitest suite once
npm run build      # type-check and produce a production build in dist/
npm run preview    # preview the production build
npm run lint       # run ESLint
```

Frontend CI (`.github/workflows/frontend-ci.yml`) runs `npm ci`, `npm run lint`, `npm run test`, and `npm run build` on Node 22 for pull requests targeting `main` and pushes to `main`, independently of the backend CI workflow.

### Manage welcome/goodbye automation from the dashboard

The guild detail page has an **Automation** tab (between Settings and Activity) where an authorized operator can manage the welcome and goodbye messages for a guild without using Discord slash commands. Each message has a card to:

- view the current configuration and its enabled/disabled state;
- edit the channel, title, message, accent color, image URL, footer, and "include bots" option (welcome adds mention-member and a link button);
- **Save** to create or update the configuration;
- **Preview** a rendered sample of the message; and
- **Enable / Disable** an existing configuration (toggle never creates one).

The dashboard and the `/welcome` and `/goodbye` slash commands share the same backend service, so both remain fully supported and apply exactly the same validation and safety rules: unknown placeholders and unsafe mentions (`@everyone`, `@here`, raw user/role/channel mentions) are rejected, `{mention}` is rejected for goodbye, image and button URLs must be HTTPS, a button needs both a label and a URL, the color must be a hex value, and Discord embed limits are enforced. Automation is authorized through the same operator-to-guild boundary as guild settings — an operator can only manage automation for guilds where they hold an active `OWNER`/`ADMIN` GuildOS authorization — and every state-changing request requires the CSRF token.

The dashboard **preview never sends a message to Discord**: it renders deterministic sample values (a sample member, username, member count, and the guild's name) and returns them for display only. The channel field uses synced Discord text and announcement channel metadata from the bot Gateway cache, exposed through an authorized `GET /api/v1/guilds/{discordGuildId}/channels` endpoint that returns only Discord channel ids, names, types, and display labels. Channel metadata is refreshed on Gateway ready, guild join, and guild-scoped channel create/update/delete events, with repeated no-change syncs suppressed from audit logging. If the synced list is empty, fails to load, or a saved channel is no longer present, the dashboard clearly falls back to manual channel-id entry while still sending the same `channelId` field to the backend. As with the slash commands, delivery still skips safely if the saved channel later becomes unavailable or the bot loses permission, without deleting the configuration.

### Review guild audit events

The guild detail page includes an **Audit Log** tab backed by:

```text
GET /api/v1/guilds/{discordGuildId}/audit-log
```

The endpoint requires an authenticated operator with active access to the guild. Unknown guilds, missing access, and revoked access all return the same `404` shape. Optional query parameters are `limit` (default 50, max 100), `eventType`, `from`, and `to` as ISO instants. Events are returned newest first and expose only `occurredAt`, `eventType`, `actorType`, `summary`, `targetType`, and `targetLabel`.

The audit log records bounded, application-generated summaries for onboarding/access changes, guild settings updates, welcome/goodbye configuration and toggles, and changed Discord channel metadata syncs. It never exposes database ids, operator ids, OAuth/session data, secrets, stack traces, message content, templates, raw Discord payloads, or moderation decisions. Moderation actions, AI analysis, realtime streaming, and custom audit rules are intentionally deferred.

## Experimental Telegram adapter (proof of concept)

Guild OS is evolving from a Discord-first backend into a multi-platform community-management core with pluggable platform adapters. A small `io.github.arturcarletto.guildos.platform` package holds adapter-neutral abstractions (`CommunityPlatform`, the platform-scoped ids, `IncomingCommunityEvent`, `PlatformBotCommand`, `PlatformMessageSender`), and the Discord integration is the first complete adapter. The `io.github.arturcarletto.guildos.telegram` package adds a **minimal, experimental Telegram adapter** as a second-platform proof of concept.

Scope is intentionally tiny: the Telegram adapter long-polls for updates and answers a single command, `/ping`, with `GuildOS is online.`. It does not onboard Telegram chats, authenticate operators, persist any activity, or deliver welcome/goodbye messages. It never stores or logs message text, and the bot token is never written to logs, actuator output, exceptions, or `toString`.

The adapter is **disabled by default**. With Telegram disabled, the application starts without a token, makes no Telegram HTTP calls, and starts no polling thread.

### Enable it locally

Create a bot with [@BotFather](https://t.me/BotFather) in the Telegram app to obtain a bot token, then set these variables in the shell session that starts Spring Boot:

```bash
export GUILDOS_TELEGRAM_ENABLED=true
export TELEGRAM_BOT_TOKEN=your-local-telegram-bot-token
```

Never commit or share a real bot token. Do not put a real token in `.env.example` or any other tracked file. When Telegram is enabled, a bot token is required; a missing or blank token fails startup fast with a safe configuration error that does not print the token. The polling interval defaults to 2 seconds and can be overridden with `GUILDOS_TELEGRAM_POLL_INTERVAL` (for example `5s`).

With PostgreSQL running, start the backend with the local profile:

```bash
cd backend
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Test `/ping` manually

1. Start the backend with Telegram enabled as above.
2. In Telegram, open a direct chat with your bot (or add it to a test group).
3. Send `/ping` (in a group, `/ping@YourBotName` also works).
4. The bot replies `GuildOS is online.`.

Any other message is ignored safely. The poller keeps running if Telegram returns an error or a malformed update, and a single bad update never crashes it. Because updates are consumed with an in-memory offset, restarting the backend may re-deliver a very recent `/ping`; this is expected for the proof of concept.

### Not implemented yet

Telegram onboarding, Telegram OAuth, Telegram dashboard support, Telegram welcome/goodbye, Telegram moderation, and a Telegram activity-ingestion bridge are deliberately deferred. The current activity pipeline is keyed to the Discord guild registry, so bridging Telegram message metadata into it would require schema and model changes; that is documented as a follow-up rather than implemented here. See [ADR 0002](docs/adr/0002-platform-adapter-architecture.md) for the rationale.

## Run tests and verification

The integration tests start PostgreSQL containers, run Flyway, load the Spring application context, and check persistence behavior against real PostgreSQL features such as `ON CONFLICT` and `FOR UPDATE SKIP LOCKED`. Docker Desktop must be running.

```bash
cd backend
sh ./mvnw test
sh ./mvnw verify
```

CI runs the same Maven `verify` lifecycle on pushes to `main` and pull requests targeting `main`.

## Configuration model

Shared configuration requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, making non-local runtime configuration explicit. The `local` profile supplies documented local-only defaults. `GUILDOS_DISCORD_ENABLED` and `GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED` default to `false`; their respective bot token or OAuth client credentials are required only when enabled. Activity processing defaults are safe and bounded under `guildos.activity.processing.*`, and automatic processing can be disabled with `GUILDOS_ACTIVITY_PROCESSING_ENABLED=false` for deterministic tests. Hibernate validates the schema but never creates or updates it; Flyway owns all schema changes.

See [the architecture overview](docs/architecture.md), [ADR 0001](docs/adr/0001-modular-monolith.md), and [ADR 0002](docs/adr/0002-platform-adapter-architecture.md) for the initial design decisions.
