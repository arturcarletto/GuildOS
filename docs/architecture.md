# Guild OS architecture

## Starting as a modular monolith

Guild OS starts as one deployable Spring Boot application organized by business capability. A modular monolith keeps local development, testing, deployment, and database consistency straightforward while the product boundaries and workload patterns are still being discovered. Package boundaries can provide separation without introducing network calls, distributed transactions, duplicated operational tooling, or premature service ownership.

The implemented `discord` package is the JDA infrastructure boundary for Gateway configuration, connection lifecycle, guild lifecycle events, guild command registration, interaction replies, activity-event adaptation, and health reporting. The `guild` package owns the persistent guild model, repository, platform-neutral commands, transactional connection use cases, and a read-only `GuildDirectory` contract that exposes safe registered-guild projections to other capabilities. The `identity` package owns Discord OAuth client configuration, Spring Security policy, local operator accounts, OAuth profile mapping, authenticated principals, and the current-operator API. The `guildaccess` package owns operator-to-guild authorization: the Discord guild client, permission and eligibility evaluation, the persistent operator-to-guild relationship, onboarding and guild-access APIs, and public authorization/onboarding read contracts for other capabilities. The `guildsettings` package owns persistent timezone and locale settings plus authorized and read-only application contracts. The `guildstatus` package owns the platform-neutral, read-only guild status use case. The `guildmembermessage` package owns the shared welcome/goodbye capability: persistent per-kind member-message configuration, deterministic template validation/rendering, embed appearance validation, and platform-neutral administration, preview, toggle and delivery use cases. The `guildactivity` package owns platform-neutral activity ingestion commands, validation, durable inbox persistence, retry/dead-letter state, asynchronous processing, UTC hourly projections, metrics, and the authorized analytics API. Other feature packages will be added when their behavior is implemented; there are no empty placeholder modules.

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
5. Each event follows the same dependency direction: Discord event -> Discord adapter -> guild application service -> PostgreSQL.
6. The guild service creates or reconnects records on connection and marks existing records disconnected on leave; it never deletes guild history. An unknown leave is an idempotent no-op.
7. The `discord` Actuator health contributor derives its status from the live JDA connection and reports only the connection status and guild count as details.
8. During Spring shutdown, the boundary requests a graceful JDA shutdown, waits for a bounded interval, and forces shutdown only if necessary.

The guild lifecycle listener handles guild ready, join, and leave signals and delegates guild command reconciliation after successful registry connection. Separate Discord listeners handle member-message delivery and activity-event adaptation. JDA types remain inside the Discord adapter; feature application boundaries accept platform-neutral commands.

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
6. Spring Security stores the authenticated principal in the server-side HTTP session and redirects to `/api/v1/me`.

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

## Initial request and database flow

1. An HTTP request reaches the embedded web server and Spring MVC routing.
2. A future feature's application layer coordinates its use case and accesses persistence through that feature's boundary.
3. Spring Data JPA uses the configured PostgreSQL data source and the `guild_os` default schema.
4. PostgreSQL commits the transaction and the web layer returns the result.

At startup, Flyway connects before JPA initialization, ensures the `guild_os` migration schema exists, and applies versioned migrations. Hibernate then validates mapped entities against that schema with `ddl-auto: validate`; it does not create or modify tables. The initial migration creates only the `guild_os` schema. Actuator supplies the standard health endpoint without a custom controller.
