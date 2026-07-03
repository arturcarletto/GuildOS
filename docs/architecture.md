# Guild OS architecture

## Starting as a modular monolith

Guild OS starts as one deployable Spring Boot application organized by business capability. A modular monolith keeps local development, testing, deployment, and database consistency straightforward while the product boundaries and workload patterns are still being discovered. Package boundaries can provide separation without introducing network calls, distributed transactions, duplicated operational tooling, or premature service ownership.

The implemented `discord` package is the JDA infrastructure boundary for Gateway configuration, connection lifecycle, guild lifecycle events, and health reporting. The `guild` package owns the persistent guild model, repository, platform-neutral commands, transactional connection use cases, and a read-only `GuildDirectory` contract that exposes safe registered-guild projections to other capabilities. The `identity` package owns Discord OAuth client configuration, Spring Security policy, local operator accounts, OAuth profile mapping, authenticated principals, and the current-operator API. The `guildaccess` package owns operator-to-guild authorization: the Discord guild client, permission and eligibility evaluation, the persistent operator-to-guild relationship, and the onboarding and guild-access APIs. Other feature packages will be added when their behavior is implemented; there are no empty placeholder modules.

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

The listener handles only guild ready, join, and leave lifecycle signals. It does not register commands or process message or member events. JDA types remain inside the Discord adapter; the guild application boundary accepts platform-neutral commands.

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

The dependency direction is: HTTP controller -> guild-access application service -> operator and guild repositories plus the Discord OAuth guild client -> PostgreSQL. OAuth eligibility (from Discord) and bot presence (from the registry) are independent conditions; onboarding requires both. Discord access and refresh tokens are never persisted in domain tables or exposed by any API; the access token flows only as a transient parameter to the Discord client. Each operator's authorization is isolated: no operator can read or revoke another's. Revocation is soft and idempotent, preserving history and allowing later reactivation. This establishes authorization foundations only; full guild management is not implemented. The authorized-client and session state is single-instance and must be revisited before horizontal scaling.

## Initial request and database flow

1. An HTTP request reaches the embedded web server and Spring MVC routing.
2. A future feature's application layer coordinates its use case and accesses persistence through that feature's boundary.
3. Spring Data JPA uses the configured PostgreSQL data source and the `guild_os` default schema.
4. PostgreSQL commits the transaction and the web layer returns the result.

At startup, Flyway connects before JPA initialization, ensures the `guild_os` migration schema exists, and applies versioned migrations. Hibernate then validates mapped entities against that schema with `ddl-auto: validate`; it does not create or modify tables. The initial migration creates only the `guild_os` schema. Actuator supplies the standard health endpoint without a custom controller.
