# Guild OS architecture

## Starting as a modular monolith

Guild OS starts as one deployable Spring Boot application organized by business capability. A modular monolith keeps local development, testing, deployment, and database consistency straightforward while the product boundaries and workload patterns are still being discovered. Package boundaries can provide separation without introducing network calls, distributed transactions, duplicated operational tooling, or premature service ownership.

The implemented `platform` package is the small, adapter-neutral seam that lets Guild OS evolve from Discord-first toward multi-platform community management. It holds immutable value objects only — `CommunityPlatform` (`DISCORD`, `TELEGRAM`), the platform-scoped ids (`PlatformCommunityId`, `PlatformActorId`, `PlatformChannelId`, `PlatformMessageId`), a privacy-conscious `IncomingCommunityEvent`, a `PlatformBotCommand`, and a `PlatformMessageSender` outbound seam — and depends on no adapter SDK. Value objects validate a non-null platform and a non-blank, trimmed external id, and `IncomingCommunityEvent` carries only safe ids and a timestamp, never message text or display names. An ArchUnit test keeps the package free of any dependency on `discord`, `telegram`, or JDA. See [ADR 0002](adr/0002-platform-adapter-architecture.md).

The implemented `discord` package is the JDA infrastructure boundary for Gateway configuration, connection lifecycle, guild lifecycle events, guild command registration, interaction replies, activity-event adaptation, channel-cache metadata sync, and health reporting. It is the first complete platform adapter.

The `telegram` package is an experimental, disabled-by-default second platform adapter, added as a proof of concept. It owns `TelegramProperties` (enabled flag, secret bot token, poll interval), a minimal Telegram Bot API client over Spring's `RestClient` (only `getUpdates` and `sendMessage`, with internal package-private wire DTOs), a `TelegramUpdatePoller` `SmartLifecycle` long-polling loop that only exists when Telegram is enabled, and a `TelegramCommandHandler` that answers a single `/ping` command with "GuildOS is online.". The adapter translates Telegram updates into the neutral `IncomingCommunityEvent` and replies through `PlatformMessageSender`; Telegram wire types never leave the package. It persists nothing, records no message text, and requires a bot token only when enabled (failing fast otherwise). Telegram onboarding, OAuth, dashboard support, welcome/goodbye, moderation, and activity persistence are intentionally deferred. The `guild` package owns the persistent guild model, repository, platform-neutral commands, transactional connection use cases, and a read-only `GuildDirectory` contract that exposes safe registered-guild projections to other capabilities. The `identity` package owns Discord OAuth client configuration, Spring Security policy, local operator accounts, OAuth profile mapping, authenticated principals, and the current-operator API. The `guildaccess` package owns operator-to-guild authorization: the Discord guild client, permission and eligibility evaluation, the persistent operator-to-guild relationship, onboarding and guild-access APIs, and public authorization/onboarding read contracts for other capabilities. The `guildsettings` package owns persistent timezone and locale settings plus authorized and read-only application contracts. The `guildstatus` package owns the platform-neutral, read-only guild status use case. The `guildmembermessage` package owns the shared welcome/goodbye capability: persistent per-kind member-message configuration, deterministic template validation/rendering, embed appearance validation, and platform-neutral administration, preview, toggle and delivery use cases. The `guildmoderation` package owns the first safe moderation use case: dashboard member timeout validation, orchestration, DTOs, safe exception mapping, and the application-facing Discord moderation port. The `discordchannel` package owns persisted Discord text/announcement channel metadata, the authorized dashboard channel-list API, and transactional upsert/inactivation logic; JDA snapshot collection stays in the `discord` adapter. The `guildactivity` package owns platform-neutral activity ingestion commands, validation, durable inbox persistence, retry/dead-letter state, asynchronous processing, UTC hourly projections, metrics, and the authorized analytics API. The `guildaudit` package owns bounded guild-scoped audit events, recording helpers for other capabilities, and the authorized audit-log API. Other feature packages will be added when their behavior is implemented; there are no empty placeholder modules.

## Intended modules

The likely capabilities are:

- community management for guild configuration and policies;
- Discord integration for event ingestion and outbound actions;
- automation for rules, scheduled work, and moderation workflows;
- analytics beyond the current hourly activity endpoint;
- identity and access for operators and authorization;
- AI-assisted features where they provide a measured product benefit.

These names describe expected architectural boundaries, not implemented features or fixed public APIs. Dependencies between future modules should point through explicit application-facing contracts rather than directly into another module's persistence details.

## Evolution path

The application should remain a modular monolith while a single deployment and database satisfy reliability, scale, and team ownership needs. Module boundaries can be reinforced with architecture tests and module-specific migrations as code appears. A capability should be extracted into a separate service only when measured scaling, isolation, deployment cadence, or ownership requirements justify the operational cost. Asynchronous messaging and separate data ownership should be introduced with such an extraction, not in anticipation of one.

## Discord Gateway runtime flow

1. Spring binds and validates `guildos.discord` configuration during startup.
2. When the integration is disabled, no JDA client or Discord health contributor is created.
3. When enabled, the Discord boundary creates one JDA client with exactly `GUILD_MEMBERS` and `GUILD_MESSAGES` Gateway intents and blocks startup for at most 30 seconds until JDA is connected and ready. `MESSAGE_CONTENT` and presence are never requested.
4. The registered Discord guild listener synchronizes JDA's current guilds on the ready event and handles explicit guild join and leave events.
5. For ready, join, channel create/delete, and generic channel update handling, the adapter snapshots supported text and announcement channels from JDA's cache outside any database transaction, then persists the active set with idempotent upserts and marks missing channels inactive.
6. Each persistence path follows the same dependency direction: Discord event -> Discord adapter -> application service -> PostgreSQL.
7. The guild service creates or reconnects records on connection and marks existing records disconnected on leave; it never deletes guild history. An unknown leave is an idempotent no-op.
8. The `discord` Actuator health contributor derives its status from the live JDA connection and reports only the connection status and guild count as details.
9. During Spring shutdown, the boundary requests a graceful JDA shutdown, waits for a bounded interval, and forces shutdown only if necessary.

The guild lifecycle listener handles guild ready, join, and leave signals, delegates guild command reconciliation after successful registry connection, and refreshes channel metadata for connected guilds on ready/join plus guild-scoped channel create, delete, and update events. Channel sync stores only supported text/announcement channel ids, names, types, positions, active flags, and sync timestamps; it does not store message content, permissions, topics, categories, or operator data. Sync failures are bounded to safe logs and do not crash Gateway listeners or application startup. Separate Discord listeners handle member-message delivery and activity-event adaptation. JDA types remain inside the Discord adapter; feature application boundaries accept platform-neutral commands.

## Discord slash command flow

The Discord adapter owns one authoritative command catalog for this application. It contains the guild-only `/status` command plus `/welcome` and `/goodbye`, each with `status`, `configure`, `preview`, and `toggle` subcommands. On a ready or guild-join event, the lifecycle listener first synchronizes the guild registry and then asks the command registrar to bulk-update the complete catalog through Discord's guild command API:

`Ready/join event -> guild registry synchronization -> authoritative command catalog -> guild bulk command update`

The bulk update is application-scoped by Discord and replaces this application's command list for that guild; it does not affect another Discord application's commands. Keeping one catalog and one registrar prevents independent listeners from overwriting each other's definitions. Registration is asynchronous, idempotent, and outside database transactions. A registration failure is safely logged by guild id and failure category and cannot undo successful guild persistence or fail application startup.

The interaction dependency direction is:

`Slash interaction -> Discord adapter -> guild status service -> public guild/onboarding/settings read contracts -> PostgreSQL -> ephemeral Discord reply`

The listener accepts only the guild-scoped `status` root command, defers an ephemeral reply before database-backed work, and ignores unrelated or direct-message interactions. The platform-neutral `guildstatus` service resolves connection state through `GuildDirectory`, onboarding state through `GuildOnboardingDirectory`, and existing settings through `GuildSettingsReader`. Missing persisted settings use in-memory `UTC`/`en-US`/version `0` defaults. Unknown or disconnected registry entries produce an unavailable result, while connected guilds with no active onboarding authorization receive a non-sensitive onboarding-required result.

JDA command, guild, interaction, and reply types remain entirely inside the `discord` adapter. The status service and its read contracts contain no JDA types. The `/status` path performs no database writes, does not materialize settings, and makes no Discord REST call while a database transaction is open. Command registration and interaction replies also occur outside database transactions. The `/status` flow itself requires no Gateway intent; member lifecycle messaging uses `GUILD_MEMBERS`, and activity ingestion additionally uses `GUILD_MESSAGES` without `MESSAGE_CONTENT`.

## Member lifecycle messaging flow

Welcome and goodbye are two variations of one capability, `guildmembermessage`, keyed by a `MemberMessageKind` enum (`WELCOME`, `GOODBYE`). A single shared platform-neutral service, store, entity, template model, appearance model and renderer serve both kinds; there is no duplicated welcome/goodbye persistence logic and no JDA type inside the capability. The Discord adapter owns all JDA concerns: interaction orchestration (`DiscordMemberMessageCommandListener`), real event delivery (`DiscordMemberLifecycleListener`), embed and message construction (`DiscordMemberMessageEmbedFactory`), channel/permission resolution (`DiscordMemberMessageChannelResolver`) and delivery metrics (`DiscordMemberMessageDeliveryMetrics`).

Both `/welcome` and `/goodbye` default to Manage Server, and the listener independently rechecks the invoking member's effective live Manage Server permission. The guild must be connected according to `GuildDirectory` and onboarded according to `GuildOnboardingDirectory`; the capability never queries another capability's table or fabricates an operator identity. Configure follows this dependency direction:

`Discord interaction -> ephemeral defer -> runtime member/channel permission checks -> platform-neutral member-message service -> guild/onboarding contracts -> transactional member-message store -> PostgreSQL -> ephemeral embed reply`

The adapter validates that the option is a standard text or announcement channel in the invoking guild and that the bot can view, send and embed links there. These JDA operations occur before the short database transaction. The service validates the appearance (templates per field, hex color, HTTPS image/button URLs, paired button, kind-specific mention/button rules, and a combined worst-case embed budget), then reads a state snapshot before mutating. When the snapshot is absent, a create runs PostgreSQL `INSERT ... ON CONFLICT (registered_guild_id, message_kind) DO NOTHING`; a successful insert returns the created row, while a lost create race affects no rows and becomes a controlled conflict rather than loading and overwriting the row a concurrent command just created. When the snapshot is present, the store reloads the managed row, compares its version against the snapshot version, and flushes only real changes. An identical configure is a no-op that touches neither timestamp nor version, and editing a disabled configuration keeps it disabled — `toggle` is the only way to change the enabled state. Existing-row changes retain JPA `@Version` as a final backstop; a stale mutation rolls back and becomes a controlled retry response rather than silently overwriting another server manager's change. Welcome and goodbye rows for the same guild are independent because the unique key includes the kind. The internal version is used only for concurrency control and never appears in any embed.

Status and preview follow read-only paths and never materialize a row. The renderer supports `{member}`, `{username}`, `{server}`, `{memberCount}` and (welcome-only) `{mention}`, is deterministic and non-recursive, and rejects mass and raw Discord mentions. Preview renders the exact public `MessageCreateData` as an ephemeral response with all mentions suppressed, so it notifies nobody and never calls a channel `sendMessage` path.

The dashboard channel picker reads through `GET /api/v1/guilds/{discordGuildId}/channels`, which authorizes the current operator with the same guild-access boundary and returns only active supported channels sorted deterministically by Discord position, name, and id. The response exposes Discord channel ids, names, types, and display labels, never database ids or operator/session data. The picker is an operator convenience: save and preview requests still send the same `channelId` field and the member-message capability keeps its existing validation and delivery safety checks.

Real delivery runs from `DiscordMemberLifecycleListener` on `GuildMemberJoinEvent` (welcome) and `GuildMemberRemoveEvent` (goodbye), which requires the privileged `GUILD_MEMBERS` intent enabled in `DiscordConfiguration`; `GUILD_MESSAGES` is enabled separately for activity metadata, while `MESSAGE_CONTENT` and presence are not. The listener reads the active configuration as a detached, immutable snapshot, so the send happens entirely outside any database transaction. Bots are ignored unless `include-bots` is set. The channel and its View Channel / Send Messages / Embed Links permissions are resolved from JDA after the read; a missing channel or permission skips delivery without any database write and without disabling the configuration. Welcome delivery restricts allowed mentions to exactly the joining user (and only when mention is enabled); goodbye mentions nobody. Because `GuildMemberRemoveEvent` covers leaves, kicks and bans, goodbye copy is neutral and no audit-log lookup or `VIEW_AUDIT_LOG` is used. Every delivery outcome increments the bounded counter `guildos.discord.member_message.delivery` tagged only by `kind` and `outcome`; failures are logged with bounded metadata (kind, guild id, channel id, failure category) and never with message content, templates or URLs.

The V7 migration evolves the single-purpose `guild_welcome_configurations` table into `guild_member_message_configurations`, migrating every existing welcome row into a `WELCOME` configuration (preserving channel, message as the description, enabled state, timestamps and version; applying sensible appearance defaults) and dropping the old table. Database constraints enforce the supported kinds, numeric channel ids, non-blank required templates, the accent-color range, HTTPS image/button URLs, button-pair consistency, non-negative versions, and the invariants that goodbye never mentions and never carries a button.

## Activity ingestion and analytics flow

The `guildactivity` capability is the first analytics implementation. It owns platform-neutral event types, the immutable ingestion command, validation, the durable PostgreSQL inbox, retry/dead-letter state, hourly projection tables, analytics queries, HTTP response models, and bounded Micrometer metrics. It depends on other capabilities only through public contracts: `GuildDirectory` confirms the guild exists in the registry, `GuildOnboardingDirectory` confirms at least one active onboarding authorization for ingestion eligibility, and `GuildAccessAuthorizer` authorizes API reads. It never imports or exposes JDA types.

The Discord adapter owns JDA event handling in `DiscordGuildActivityListener`:

`JDA event -> Discord adapter -> IngestGuildActivityCommand -> guildactivity ingestion service`

The listener maps `GuildMemberJoinEvent`, `GuildMemberRemoveEvent`, `MessageReceivedEvent`, `MessageUpdateEvent`, `MessageDeleteEvent`, and `MessageBulkDeleteEvent`. Direct messages and unsupported/system contexts are ignored safely. Bulk deletes become one `MESSAGE_DELETED` command per message id. Message-created commands use Discord's message creation timestamp; update/delete commands use one captured `Clock.instant()` for the callback when no source timestamp is available. Member-left source ids are documented as best-effort replay deduplication because Discord provides no native leave event id.

The adapter captures only ids, bot flags, event type, and timestamps. It never calls message-content APIs and never reads, hashes, persists, logs, or returns message text, embeds, attachment names or URLs, component custom ids, sticker names, poll answers, usernames, display names, guild names, or channel names as activity payload data. The enabled Gateway intent set is exactly `GUILD_MEMBERS` and `GUILD_MESSAGES`; `MESSAGE_CONTENT` is absent.

Ingestion is deliberately short:

`neutral command -> validate snowflakes and type fields -> resolve registered guild -> confirm onboarding -> INSERT PENDING event`

The insert uses `INSERT ... ON CONFLICT (source_event_id) DO NOTHING`, so duplicate source ids are atomic no-ops and cannot increment analytics again. Unknown and non-onboarded guilds return ignored outcomes without an event row. The ingestion transaction does no projection work, Discord REST call, or long-running operation.

PostgreSQL is the durable queue for this stage. The processor is a non-transactional orchestrator invoked by a scheduler. A short claim transaction selects eligible `PENDING` rows and stale `PROCESSING` rows with `FOR UPDATE SKIP LOCKED`, sets `PROCESSING`, records `locked_at`, increments `attempt_count`, and returns immutable snapshots, including whether the row was actually reclaimed from expired `PROCESSING`. Each claimed event is then processed in its own short transaction. That transaction verifies the active claim, updates the hourly projection, and marks the inbox row `PROCESSED` atomically. If projection fails, the projection transaction rolls back; a separate short transaction stores only a bounded failure category and either reschedules the row with capped exponential backoff or marks it `DEAD` after the maximum attempts. If the active claim was lost, the worker records `claim_lost` and does not write projection or retry state from the stale snapshot. A dead row is not retried automatically. Stale locks let future workers recover rows after a crash.

Projection writes complete UTC hourly buckets keyed by registered guild id and bucket start. `MESSAGE_CREATED` increments created-message and human/bot counters and registers distinct hourly actor/channel ids when present. `MESSAGE_EDITED` and `MESSAGE_DELETED` count distinct messages because their source ids are stable per guild/message/type. `MEMBER_JOINED` and `MEMBER_LEFT` increment lifecycle counters. Companion uniqueness tables preserve idempotent hourly active member/channel counts under retries and concurrent workers. This provides PostgreSQL-backed at-least-once ingestion and idempotent projection, not exactly-once delivery semantics.

The read path is:

`HTTP -> @AuthenticationPrincipal AuthenticatedOperator -> GuildAccessAuthorizer -> hourly analytics query -> JSON response`

`GET /api/v1/guilds/{discordGuildId}/analytics/activity?from=<instant>&to=<instant>` accepts an inclusive `from`, exclusive `to`, and a maximum range of 31 days. Both instants must be exact UTC-hour boundaries; the service rejects partial-hour values rather than rounding, truncating, expanding, or shrinking the requested range. Missing guilds, missing access, and revoked access are indistinguishable `404` responses. Reads remain available while the bot is disconnected if Guild OS authorization is still active. Responses expose only the Discord guild id requested, UTC range, summary counters, and ordered complete-hour buckets; they never expose internal guild UUIDs, inbox ids, processing state, retry/failure data, raw user/channel/message ids, operator data, sessions, or tokens.

Metrics use bounded tags only: activity event type and outcome. Processing outcomes distinguish `processed`, `retry_scheduled`, `dead`, `claim_lost`, and `stale_reclaimed`; stale reclamation is emitted only for a claim query that actually recovered an expired `PROCESSING` row. Guild, channel, message, user, URL, and exception-message values are never used as metric tags. RabbitMQ, Kafka, Redis, a separate analytics database, real-time dashboards, moderation automation, message-content analysis, retention automation, and distributed deployment are intentionally deferred until observed scale or ownership pressure justifies that operational cost.

## Operator authentication flow

1. A browser explicitly requests `/oauth2/authorization/discord`.
2. Spring Security creates the authorization request and OAuth state, then redirects to Discord with only the `identify` and `guilds` scopes.
3. Spring Security handles the authorization-code callback and retrieves Discord user information.
4. The identity OAuth adapter validates and maps safe profile attributes into a platform-neutral login command.
5. The identity service creates or updates the operator account in PostgreSQL and returns the local operator identity.
6. Spring Security stores the authenticated principal in the server-side HTTP session and redirects to the configured success URL (`guildos.identity.discord-oauth.success-redirect-uri`, default `/api/v1/me`).

The dependency direction is: Browser -> Spring Security OAuth2 -> Discord user-info -> identity service -> PostgreSQL -> authenticated session. This human operator OAuth login is separate from the JDA bot Gateway in the `discord` package; the two integrations are enabled independently, and neither depends on the other. Access and refresh tokens are not stored in Guild OS domain tables or exposed through the API. Authentication establishes operator identity; the `guildaccess` capability, described next, builds the first authorization boundary on top of it. Sessions are local to the single application instance and require a deliberate scaling design before multiple instances are deployed.

## Guild onboarding and operator authorization flow

The `guildaccess` package answers "which guilds may this operator manage?". It is deliberately a separate capability from `identity` (which authenticates operators) and `guild` (which owns the bot registry): keeping authentication, the bot registry, and operator-to-guild authorization in distinct packages gives each clear ownership, and `guildaccess` depends on the other two only through their public application-facing contracts (`AuthenticatedOperator` and `GuildDirectory`). This aligns with the intended "identity and access" boundary while avoiding a generic catch-all package.

1. An authenticated operator calls an onboarding or guild-access endpoint under `/api/v1/**`.
2. The controller resolves the operator principal and, for Discord-backed reads, loads the operator's OAuth access token from Spring Security's authorized-client store; a missing authorized client yields a controlled JSON 401 rather than a redirect.
3. The guild-access application service verifies eligibility from the operator's current Discord guild list, mapping Discord ownership or the `ADMINISTRATOR`/`MANAGE_GUILD` permission bits to a Guild OS role; it never trusts a client-supplied claim.
4. The service confirms the bot is currently connected to the guild through the `GuildDirectory` registry contract.
5. It creates, reactivates, updates, or revokes the persistent operator-to-guild authorization in PostgreSQL, capturing one clock instant per operation.

The dependency direction is: HTTP controller -> guild-access application service -> operator and guild repositories plus the Discord OAuth guild client -> PostgreSQL. OAuth eligibility (from Discord) and bot presence (from the registry) are independent conditions; onboarding requires both. Discord access and refresh tokens are never persisted in domain tables or exposed by any API; the access token flows only as a transient parameter to the Discord client. Each operator's authorization is isolated: no operator can read or revoke another's. Revocation is soft and idempotent, preserving history and allowing later reactivation. This authorization boundary now supports the `guildsettings` capability; broader guild management remains unimplemented. The authorized-client and session state is single-instance and must be revisited before horizontal scaling.

## Authorized guild settings flow

The `guildsettings` capability owns the first guild-management resource: one persistent timezone and locale configuration per registered guild. It depends on `guildaccess` only through the public `GuildAccessAuthorizer` and `AuthorizedGuildAccess` application contracts. It does not access the guild-access or guild registry entities, repositories, or tables directly, and it never accepts a client-supplied operator id, role, internal guild id, or authorization claim.

The dependency direction is:

`HTTP -> guildsettings application service -> guildaccess authorization contract + guildsettings repository -> PostgreSQL`

1. The controller takes operator identity only from the authenticated principal.
2. The authorization contract validates the Discord guild id, resolves the registered guild through `GuildDirectory`, and requires a non-revoked authorization for that operator.
3. On first authorized access, the settings store uses `INSERT ... ON CONFLICT DO NOTHING` to safely materialize `UTC` and `en-US`, including under concurrent requests.
4. Updates canonicalize the Java `ZoneId` and BCP 47 locale before comparing them with the stored values and require the caller's current `expectedVersion`.
5. A mutation locks the active operator authorization first and the shared settings row second. Revocation uses the same authorization lock, so an update that follows a revocation lock or commit cannot write. The settings row lock serializes updates by different authorized operators.
6. A real update flushes before response mapping so the returned JPA version is current. A canonical no-op changes neither version nor `updatedAt`; a stale version returns a conflict without partial mutation.

Each settings transaction captures one clock instant and reuses it for all timestamps it writes. Reads and updates remain available while the bot registry entry is disconnected because active Guild OS authorization, rather than live Gateway state, governs this capability. No Discord HTTP request occurs inside or outside a settings read/update transaction.

## Moderation action flow

The `guildmoderation` capability owns the first moderation action foundation: creating a Discord member communication timeout from the authenticated dashboard API. It is intentionally narrow and does not include member search, warnings, kick, ban, message deletion, queues, AI moderation, appeal workflows, realtime moderation streams, or a dedicated moderation action history table beyond the guild audit log.

The dependency direction is:

`HTTP -> guildmoderation application service -> guildaccess authorization contract -> Discord moderation port -> guildaudit recorder`

The controller takes operator identity only from `@AuthenticationPrincipal AuthenticatedOperator` and accepts a minimal request body: target Discord user id, duration in minutes, and an optional bounded reason. Validation requires a Discord snowflake target id, a duration from 1 minute through 28 days, and a non-blank reason when a reason is provided. Requests cannot supply an operator id, role, registered guild id, authorization state, or internal database id. Missing guilds, missing access, and revoked access all return the same safe `404` shape.

The application service is a non-transactional orchestrator. It first uses `GuildAccessAuthorizer.findActive` to authorize the operator in a short read transaction, then calls the application-facing `GuildModerationDiscordClient` outside any database transaction. The Discord adapter implementation (`JdaGuildModerationDiscordClient`) is the only place that imports JDA. It obtains the live JDA instance from the gateway, resolves the guild and target member, checks the bot's `MODERATE_MEMBERS` permission, and executes the timeout as an auditable Discord REST action. Missing guilds, missing members, missing bot permission, Discord rejection, and service/rate failures are mapped to controlled `ModerationDiscordActionException` categories. Adapter logs include only bounded metadata: action type, guild id, target user id, failure category, and failure class. They never include raw Discord payloads, tokens, message content, display names, or raw exception messages.

Only after Discord accepts the timeout does the service record `MEMBER_TIMEOUT_CREATED` through `GuildAuditRecorder`, which performs one short local audit append. The API response exposes only the Discord guild id, action type, target Discord user id, duration, and status. It does not expose internal UUIDs, operator ids, OAuth/session/token data, or the moderation reason. The audit event uses an application-generated summary and generic target label (`Member timeout`) rather than raw reason text or user-facing Discord names.

## Guild audit log flow

The `guildaudit` capability is an append-only, guild-scoped audit foundation for operator-visible management history. It stores bounded enum event types, bounded actor types (`OPERATOR`, `SYSTEM`, `DISCORD`), an occurred timestamp, an application-generated summary, and optional bounded target metadata. It references the registered guild and optionally the operator account for persistence integrity, but the API never returns those internal ids.

Recording flows point inward through `GuildAuditRecorder`:

`settings/access/member-message/channel sync/moderation capability -> GuildAuditRecorder -> guildaudit store -> PostgreSQL`

Each audit append captures one `Clock.instant()` for that event and performs one short local write. Existing transactional capabilities call the recorder after the protected state change succeeds, so a rollback also rolls back the audit row. The moderation capability calls the recorder only after Discord accepts the timeout, keeping failed outbound actions out of the success audit log. Dashboard settings, automation changes, and member timeout actions are recorded as `OPERATOR`; slash-command welcome/goodbye administration is recorded as `DISCORD`; changed channel metadata syncs are recorded as `SYSTEM`. Channel metadata snapshots are collected in the Discord adapter before the transactional sync service runs, and the sync records an audit event only when the active supported text/announcement channel set actually changes.

The read path is:

`HTTP -> @AuthenticationPrincipal AuthenticatedOperator -> GuildAccessAuthorizer -> guildaudit query -> JSON response`

`GET /api/v1/guilds/{discordGuildId}/audit-log` accepts `limit` (default 50, max 100), optional `eventType`, and optional inclusive `from` / exclusive `to` ISO instants. Missing guilds, missing access, and revoked access are indistinguishable `404` responses. Results are ordered newest first and expose only the Discord guild id plus event time, event type, actor type, summary, target type, and target label. Responses never expose internal UUIDs, operator ids, OAuth/session data, secrets, stack traces, message content, Discord payloads, raw templates, raw moderation reasons, or Discord display names. AI analysis, realtime streaming, retention policies, custom audit rules, and broader moderation workflows remain out of scope.

## Operator dashboard frontend

The `frontend/` directory holds the first operator dashboard: a separate React + TypeScript single-page application built with Vite and tested with Vitest, React Testing Library, and jsdom. It is a distinct application in the monorepo, not a rewrite of or a dependency for the backend. The backend keeps ownership of Discord OAuth, the server-side session, CSRF, and every authorization decision; the frontend is a thin, security-conscious client over the existing `/api/**` and auth endpoints.

The dependency direction is one way:

`React pages -> typed API client -> browser fetch (credentials: include) -> Vite dev proxy or same-origin -> Spring Security + /api/** controllers`

Session and CSRF handling is centralized in `src/api/client.ts`. Every request sends `credentials: "include"` so the server-side session cookie flows on same-origin calls. GET requests never carry a CSRF token. State-changing requests (`POST`/`PUT`/`DELETE` and `POST /logout`) fetch the token from `GET /api/v1/csrf`, cache it in memory only, and send it using the header name the backend returns rather than a hardcoded header. A 403 on a state-changing request invalidates the cached token, re-fetches once, and retries, which absorbs a rotated or stale token. Non-OK responses become a typed `ApiError` carrying the HTTP status and best-effort parsed body; 401 is treated as signed-out, and 409 on settings drives a reload of the latest server state. The client never reads, stores, or logs OAuth or session tokens, and never persists the CSRF token to web storage. Responses are parsed defensively from `unknown`, so a minor backend field addition degrades to a safe partial render instead of a crash.

Routing uses `react-router-dom` with a public landing route, an authenticated dashboard shell (`/dashboard`, `/dashboard/guilds`, `/dashboard/guilds/:discordGuildId`), and a not-found fallback. An `AuthProvider` probes `GET /api/v1/me` once, exposes the current operator, and the shell redirects unauthenticated visitors to the landing page. Analytics range controls generate only UTC-hour-aligned `from`/`to` instants in the client to satisfy the endpoint's boundary rules, and cap the range at 31 days before calling the API. The guild detail Moderation tab is a thin form over `POST /api/v1/guilds/{discordGuildId}/moderation/timeout`; it does not perform member search or store any OAuth/session data in the browser.

During local development the Vite dev server (port 5173) proxies `/api`, `/oauth2`, `/login`, and `/logout` to the backend at `http://localhost:8080`, so browser requests stay same-origin and no CORS relaxation or Spring Security change is required. Discord OAuth still completes on the backend origin because the registered redirect URI points at port 8080; the shared `localhost` session cookie then authorizes the frontend's proxied `/api/**` calls. The post-login destination is a server-side configured property, `guildos.identity.discord-oauth.success-redirect-uri` (env `DISCORD_OAUTH_SUCCESS_REDIRECT_URI`), which defaults to `/api/v1/me` and is set to `http://localhost:5173/dashboard` under the `local` profile so operators return to the dashboard; the validation that requires a client id and secret whenever OAuth is enabled is unchanged. Serving the built assets from Spring Boot in production remains a deliberate follow-up rather than a shortcut that would weaken the security model.

## Initial request and database flow

1. An HTTP request reaches the embedded web server and Spring MVC routing.
2. A future feature's application layer coordinates its use case and accesses persistence through that feature's boundary.
3. Spring Data JPA uses the configured PostgreSQL data source and the `guild_os` default schema.
4. PostgreSQL commits the transaction and the web layer returns the result.

At startup, Flyway connects before JPA initialization, ensures the `guild_os` migration schema exists, and applies versioned migrations. Hibernate then validates mapped entities against that schema with `ddl-auto: validate`; it does not create or modify tables. The initial migration creates only the `guild_os` schema. Actuator supplies the standard health endpoint without a custom controller.
