# Guild OS

Guild OS is a platform for managing, automating, and analyzing Discord communities. This repository currently contains the production-oriented foundation for its backend, an optional Discord Gateway connection, a persistent registry of connected guilds, optional Discord OAuth2 login for human operators, and guild onboarding that authorizes operators to manage specific guilds.

## Project status

The project is at the initial bootstrap stage. It provides a runnable Spring Boot service, PostgreSQL persistence foundation, Flyway migrations, real-database integration tests, local Docker Compose infrastructure, backend CI, a monitored Discord Gateway connection, a persistent guild registry, server-side operator authentication through Discord OAuth2, and operator-to-guild authorization that lets an authenticated operator onboard guilds they are eligible to manage. Bot Gateway and human OAuth integrations are independently disabled by default. This establishes authorization foundations only; commands, message and member event collection, full guild management, community analytics, automation, AI features, and a frontend are not implemented yet.

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
- PowerShell 7 or Windows PowerShell 5.1

Maven does not need to be installed; the repository includes the Maven Wrapper.

## Start locally on Windows PowerShell

From the repository root, optionally create a local Compose environment file:

```powershell
Copy-Item .env.example .env
```

The committed values are non-sensitive local-development defaults. Change the values in `.env` if they conflict with your environment. The root `.env` file is read by Docker Compose only; a Spring Boot process started directly uses the `local` profile and variables from its shell environment.

Start PostgreSQL and wait until its health status is `healthy`:

```powershell
docker compose up -d postgres
docker compose ps
```

Run the backend with the `local` profile:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The local profile connects to `jdbc:postgresql://localhost:5432/guildos` with the documented local-only `guildos_app` / `guildos_local` credentials. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can override them.

In another PowerShell window, verify application health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

The response should report `status` as `UP`. Actuator exposes only `health` and `info`, and health details are not public.

Stop local infrastructure from the repository root:

```powershell
docker compose down
```

Add `-v` only when you intentionally want to delete the local PostgreSQL volume.

## Connect to the Discord Gateway

The Discord Gateway integration is disabled by default, so the backend starts without a Discord token. To enable it temporarily, create a bot in the Discord Developer Portal, install it in a test server, and set these variables in the PowerShell session that will start Spring Boot:

```powershell
$env:GUILDOS_DISCORD_ENABLED = "true"
$env:DISCORD_BOT_TOKEN = "your-local-token"
```

Never commit or share a Discord bot token. Do not put a real token in `.env.example` or another tracked file.

With PostgreSQL running, start the application from the repository root:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Startup waits until JDA reports the initial Gateway connection as ready. The integration then synchronizes the guilds currently available to JDA into PostgreSQL. Guild join events create or reconnect registry entries, and guild leave events mark entries disconnected without deleting their history.

The integration uses no optional Gateway intents and does not require Message Content, Guild Members, or Guild Presences. No guild-management API is exposed yet, and guild onboarding and authorization are not implemented. Operator authentication is available separately through Discord OAuth2, described below.

Check the existing Actuator health endpoint from another PowerShell window:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

When enabled, the `discord` health contributor is `UP` only while JDA is connected. The current safe health policy exposes the aggregate status but does not expose component details publicly.

After stopping the application, remove the credentials from the current PowerShell session:

```powershell
Remove-Item Env:GUILDOS_DISCORD_ENABLED
Remove-Item Env:DISCORD_BOT_TOKEN
```

## Authenticate operators with Discord OAuth2

Human operator login is separate from the JDA bot connection and is disabled by default. The flow requests only the `identify` and `guilds` scopes; it never requests bot-installation scopes. In the Discord Developer Portal, add this OAuth2 redirect URI:

```text
http://localhost:8080/login/oauth2/code/discord
```

Set the OAuth client credentials only in the PowerShell session that starts Spring Boot:

```powershell
$env:GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED = "true"
$env:DISCORD_OAUTH_CLIENT_ID = "your-client-id"
$env:DISCORD_OAUTH_CLIENT_SECRET = "your-client-secret"
$env:DISCORD_OAUTH_REDIRECT_URI = "http://localhost:8080/login/oauth2/code/discord"
```

Never commit or share the client secret. With PostgreSQL running, start the backend with the local profile as described above, then open this explicit login entry point in a browser:

```text
http://localhost:8080/oauth2/authorization/discord
```

After Discord authentication, the backend redirects to `GET /api/v1/me`. That protected endpoint returns only the local operator ID and safe Discord profile fields. Discord access and refresh tokens are never stored in Guild OS domain tables, and OAuth tokens and client secrets are never exposed by any API. Authentication establishes operator identity; onboarding a guild to authorize its management is described in the next section.

Spring Security uses a server-side HTTP session. Logout uses `POST /logout` and requires the active CSRF token; successful logout returns HTTP 204. The current in-memory, single-instance session strategy must be revisited before horizontal scaling rather than adding distributed session storage prematurely.

After local testing, remove the OAuth variables from the current PowerShell session:

```powershell
Remove-Item Env:GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED
Remove-Item Env:DISCORD_OAUTH_CLIENT_ID
Remove-Item Env:DISCORD_OAUTH_CLIENT_SECRET
Remove-Item Env:DISCORD_OAUTH_REDIRECT_URI
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

As with the login session, this authorized-client and session strategy is single-instance and must be revisited before horizontal scaling. This task establishes authorization foundations only; it does not implement full guild management. To try it locally, enable Discord OAuth as above (the `guilds` scope is requested automatically), sign in through `http://localhost:8080/oauth2/authorization/discord`, then call the endpoints above from the authenticated browser session.

## Run tests and verification

The integration test starts its own PostgreSQL container, runs Flyway, loads the Spring application context, and checks the resulting schema. Docker Desktop must be running.

```powershell
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

CI runs the same Maven `verify` lifecycle on pushes to `main` and pull requests targeting `main`.

## Configuration model

Shared configuration requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, making non-local runtime configuration explicit. The `local` profile supplies documented local-only defaults. `GUILDOS_DISCORD_ENABLED` and `GUILDOS_IDENTITY_DISCORD_OAUTH_ENABLED` default to `false`; their respective bot token or OAuth client credentials are required only when enabled. Hibernate validates the schema but never creates or updates it; Flyway owns all schema changes.

See [the architecture overview](docs/architecture.md) and [ADR 0001](docs/adr/0001-modular-monolith.md) for the initial design decisions.
