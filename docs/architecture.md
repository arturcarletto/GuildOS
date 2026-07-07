# Guild OS architecture

## Starting as a modular monolith

Guild OS starts as one deployable Spring Boot application organized by business capability. A modular monolith keeps local development, testing, deployment, and database consistency straightforward while the product boundaries and workload patterns are still being discovered. Package boundaries can provide separation without introducing network calls, distributed transactions, duplicated operational tooling, or premature service ownership.

The implemented `discord` package is the JDA infrastructure boundary for Gateway configuration, connection lifecycle, guild lifecycle events, guild command registration and interaction replies, and health reporting. The `guild` package owns the persistent guild model, repository, platform-neutral commands, transactional connection use cases, and a read-only `GuildDirectory` contract that exposes safe registered-guild projections to other capabilities. The `identity` package owns Discord OAuth client configuration, Spring Security policy, local operator accounts, OAuth profile mapping, authenticated principals, and the current-operator API. The `guildaccess` package owns operator-to-guild authorization: the Discord guild client, permission and eligibility evaluation, the persistent operator-to-guild relationship, onboarding and guild-access APIs, and public authorization/onboarding read contracts for other capabilities. The `guildsettings` package owns persistent timezone and locale settings plus authorized and read-only application contracts. The `guildstatus` package owns the platform-neutral, read-only guild status use case. The `guildwelcome` package owns persistent welcome configuration, deterministic template validation/rendering, and platform-neutral administration and preview use cases. Other feature packages will be added when their behavior is implemented; there are no empty placeholder modules.

## Intended modules

The likely capabilities are:

- community management for guild configuration and policies;
- Discord integration for event ingestion and outbound actions;
- automation for rules, scheduled work, and moderation workflows;
- analytics for community activity and operational reporting;
- identity and access for operators and authorization;
- AI-assisted features where they provide a measured product benefit.

These names describe expected architectural boundaries, not implemented features or fixed public APIs. Dependencies between future modules should point through explicit application-facing contracts rather than directly into another module's persistence details.

## Evolution path

The application should remain a modular monolith while a single deployment and database satisfy reliability, scale, and team ownership needs. Module boundaries can be reinforced with architecture tests and module-specific migrations as code appears. A capability should be extracted into a separate service only when measured scaling, isolation, deployment cadence, or ownership requirements justify the operational cost. Asynchronous messaging and separate data ownership should be introduced with such an extraction, not in anticipation of one.

## Discord Gateway runtime flow

1. Spring binds and validates `guildos.discord` configuration during startup.
2. When the integration is disabled, no JDA client or Discord health contributor is created.
3. When enabled, the Discord boundary creates one JDA client with no optional Gateway intents and blocks startup for at most 30 seconds until JDA is connected and ready.
4. The registered Discord guild listener synchronizes JDA's current guilds on the ready event and handles explicit guild join and leave events.
5. Each event follows the same dependency direction: Discord event -> Discord adapter -> guild application service -> PostgreSQL.
6. The guild service creates or reconnects records on connection and marks existing records disconnected on leave; it never deletes guild history. An unknown leave is an idempotent no-op.
7. The `discord` Actuator health contributor derives its status from the live JDA connection and reports only the connection status and guild count as details.
8. During Spring shutdown, the boundary requests a graceful JDA shutdown, waits for a bounded interval, and forces shutdown only if necessary.

The lifecycle listener handles guild ready, join, and leave signals and delegates guild command reconciliation after successful registry connection. It does not process message or member events. JDA types remain inside the Discord adapter; the guild application boundary accepts platform-neutral commands.

## Discord slash command flow

The Discord adapter owns one authoritative command catalog for this application. It contains the guild-only `/status` command and `/welcome` with its `status`, `configure`, `preview`, and `disable` subcommands. On a ready or guild-join event, the lifecycle listener first synchronizes the guild registry and then asks the command registrar to bulk-update the complete catalog through Discord's guild command API:

`Ready/join event -> guild registry synchronization -> authoritative command catalog -> guild bulk command update`

The bulk update is application-scoped by Discord and replaces this application's command list for that guild; it does not affect another Discord application's commands. Keeping one catalog and one registrar prevents independent listeners from overwriting each other's definitions. Registration is asynchronous, idempotent, and outside database transactions. A registration failure is safely logged by guild id and failure category and cannot undo successful guild persistence or fail application startup.

The interaction dependency direction is:

`Slash interaction -> Discord adapter -> guild status service -> public guild/onboarding/settings read contracts -> PostgreSQL -> ephemeral Discord reply`

The listener accepts only the guild-scoped `status` root command, defers an ephemeral reply before database-backed work, and ignores unrelated or direct-message interactions. The platform-neutral `guildstatus` service resolves connection state through `GuildDirectory`, onboarding state through `GuildOnboardingDirectory`, and existing settings through `GuildSettingsReader`. Missing persisted settings use in-memory `UTC`/`en-US`/version `0` defaults. Unknown or disconnected registry entries produce an unavailable result, while connected guilds with no active onboarding authorization receive a non-sensitive onboarding-required result.

JDA command, guild, interaction, and reply types remain entirely inside the `discord` adapter. The status service and its read contracts contain no JDA types. The `/status` path performs no database writes, does not materialize settings, and makes no Discord REST call while a database transaction is open. Command registration and interaction replies also occur outside database transactions. No new Gateway intents are enabled.

## Welcome configuration and preview flow

The dedicated welcome interaction listener handles only the `welcome` root command. Discord's default command permission is Manage Server for discoverability, while the listener independently rechecks the invoking member's effective live Manage Server permission. The guild must also be connected according to `GuildDirectory` and onboarded according to `GuildOnboardingDirectory`; the welcome capability never queries another capability's table or fabricates an operator identity.

Configure follows this dependency direction:

`Discord interaction -> ephemeral defer -> runtime member/channel permission checks -> platform-neutral welcome service -> guild/onboarding contracts -> transactional welcome store -> PostgreSQL -> ephemeral Discord reply`

The Discord adapter validates that the option is a standard text or announcement channel in the invoking guild and that the bot can view and send to it. These JDA operations occur before the short database transaction. The service normalizes and validates the template, then reads a state snapshot before mutating. When the snapshot is absent, a create runs PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`; a successful insert returns the created row, while a lost create race affects no rows and becomes a controlled conflict rather than loading and overwriting the row a concurrent command just created. When the snapshot is present, the store reloads the managed row, compares its version against the snapshot version, applies the desired state, and flushes real changes before mapping the version. An enabled identical configuration is a no-op that touches neither timestamp nor version. Existing-row changes also retain JPA `@Version` as a final backstop; a stale mutation rolls back and becomes a controlled retry response rather than silently overwriting another server manager's change. Disable follows the same snapshot-versioned path, preserves the channel and template, and an already-disabled current configuration is a no-op.

Status and preview follow read-only paths and never materialize a configuration row:

`Discord interaction -> ephemeral defer -> welcome service read -> deterministic template renderer -> ephemeral preview`

The renderer supports only `{member}`, `{server}`, and `{memberCount}`, bounds output, normalizes line endings, and prevents recursive replacement. Templates reject mass mentions and raw Discord mention syntax. Every administrative edit disables allowed mentions. Status and preview resolve the stored channel from JDA only after the database read; a deleted or inaccessible channel produces a warning without mutating persistence. Preview may render an enabled or disabled configuration but never calls a channel `sendMessage` path.

All JDA types remain in `discord`; `guildwelcome` contains no JDA dependency. Configure and disable transactions contain no Discord calls, and status/preview are read-only. The command catalog remains the sole registration owner and uses the existing guild bulk update—there is no global registration. No additional Gateway intent or bot Administrator permission is introduced. Real member-join handling and welcome delivery are explicitly deferred to GUILD-009.

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
