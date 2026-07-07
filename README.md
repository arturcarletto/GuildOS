# Guild OS

Guild OS is a platform for managing, automating, and analyzing Discord communities. This repository currently contains the production-oriented foundation for its backend, an optional Discord Gateway connection, a persistent registry of connected guilds, optional Discord OAuth2 login for human operators, guild onboarding that authorizes operators to manage specific guilds, authorized persistent guild settings, the guild-scoped `/status` command, and Discord administration of persistent welcome configuration and ephemeral previews.

## Project status

The project is at the initial bootstrap stage. It provides a runnable Spring Boot service, PostgreSQL persistence foundation, Flyway migrations, real-database integration tests, local Docker Compose infrastructure, backend CI, a monitored Discord Gateway connection, a persistent guild registry, server-side operator authentication through Discord OAuth2, operator-to-guild authorization, persistent per-guild timezone and locale settings, an ephemeral read-only Discord status command, and persistent welcome configuration with ephemeral administration and preview commands. Bot Gateway and human OAuth integrations are independently disabled by default. Welcome delivery, message and member event collection, moderation rules, broader guild management, community analytics, automation, AI features, and a frontend are not implemented yet.

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
|-- .github/workflows/backend-ci.yml
|-- backend/
|   |-- .mvn/wrapper/
|   |-- src/main/
|   |-- src/test/
|   |-- mvnw
|   |-- mvnw.cmd
|   `-- pom.xml
|-- docs/
|   |-- adr/0001-modular-monolith.md
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

Maven does not need to be installed; the repository includes the Maven Wrapper.

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

The integration uses no optional Gateway intents and does not require Message Content, Guild Members, or Guild Presences. Guild onboarding, operator authorization, and persistent timezone/locale settings are available through the authenticated APIs described below. Operator authentication is provided separately through Discord OAuth2.

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

Command limitations are deliberate: there is no real welcome delivery, moderation, message/member collection, analytics, scheduled automation, AI functionality, command localization, global command rollout, or frontend.

## Manage welcome configuration from Discord

The authoritative guild command catalog also registers `/welcome` with four subcommands:

- `/welcome status` reads the current configuration.
- `/welcome configure channel:<channel> message:<template>` creates or updates the configuration and automatically enables it.
- `/welcome preview` renders the saved template only in the invoking server manager's ephemeral command response; it sends nothing to the configured channel.
- `/welcome disable` disables delivery while preserving the selected channel and template for later preview or re-enabling.

Discord exposes `/welcome` to members with Manage Server by default, and Guild OS independently rechecks the invoking member's effective Manage Server permission on every interaction. Discord owner and administrator semantics are handled by JDA's effective permission check. The guild must also be currently connected and have at least one active Guild OS onboarding authorization. Discord command administration does not fabricate an operator identity or require the invoking member to have used browser OAuth.

The configure channel must be a standard guild text or announcement channel in the invoking guild. The bot must have effective View Channel and Send Messages permissions in that channel; Administrator permission is neither requested nor required. A deleted or newly inaccessible saved channel is reported safely by status and preview without deleting the persisted configuration.

Templates are normalized to LF line endings, trimmed only at their outer boundary, and limited to 1000 stored characters. They may be static or use these deterministic placeholders:

- `{member}` — the invoking member's safe effective display name in preview;
- `{server}` — the current safe guild name;
- `{memberCount}` — the current JDA guild member count.

Unknown placeholders, `@everyone`, `@here`, and raw Discord user, role, or channel mention syntax are rejected. Administrative replies disable all allowed mentions. Previewing a disabled configuration is supported and clearly labeled, but no public Discord message is sent.

To verify the feature manually in a test guild:

1. Start PostgreSQL, enable the Discord Gateway, enable human Discord OAuth, and start Guild OS.
2. Install the bot with the `bot` and `applications.commands` scopes, then grant it View Channel and Send Messages only in the intended test channel.
3. Complete browser OAuth onboarding for the connected test guild.
4. Confirm command reconciliation exposes `/status` plus `/welcome status`, `/welcome configure`, `/welcome preview`, and `/welcome disable`.
5. As a member without Manage Server, confirm welcome administration is unavailable or denied; then repeat as a member with Manage Server.
6. Configure `#welcome` with `Welcome {member} to {server}! You are member #{memberCount}.` and confirm the response is ephemeral.
7. Run status and preview, confirm the rendered values, and confirm no message appears in `#welcome`.
8. Disable the configuration, confirm status remains persisted, and confirm preview still works with a disabled warning.
9. Remove the bot's Send Messages permission and confirm a new configure attempt is rejected. Delete the configured channel and confirm status/preview report it as unavailable without deleting the row.
10. Restart Guild OS and confirm the configuration persists.

This milestone intentionally does not handle `GuildMemberJoinEvent` or deliver welcome messages. No Guild Members privileged intent—or any other optional Gateway intent—is enabled. Member-join delivery and its intent assessment belong to GUILD-009.

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

After Discord authentication, the backend redirects to `GET /api/v1/me`. That protected endpoint returns only the local operator ID and safe Discord profile fields. Discord access and refresh tokens are never stored in Guild OS domain tables, and OAuth tokens and client secrets are never exposed by any API. Authentication establishes operator identity; onboarding a guild to authorize its management is described in the next section.

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

## Run tests and verification

The integration test starts its own PostgreSQL container, runs Flyway, loads the Spring application context, and checks the resulting schema. Docker Desktop must be running.

```bash
cd backend
sh ./mvnw test
sh ./mvnw verify
```

CI runs the same Maven `verify` lifecycle on pushes to `main` and pull requests targeting `main`.

## Configuration model

Shared configuration requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, making non-local runtime configuration explicit. The `local` profile supplies documented local-only defaults. `GUILDOS_DISCORD_ENABLED` and `GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED` default to `false`; their respective bot token or OAuth client credentials are required only when enabled. Hibernate validates the schema but never creates or updates it; Flyway owns all schema changes.

See [the architecture overview](docs/architecture.md) and [ADR 0001](docs/adr/0001-modular-monolith.md) for the initial design decisions.
