# Guild OS architecture

## Starting as a modular monolith

Guild OS starts as one deployable Spring Boot application organized by business capability. A modular monolith keeps local development, testing, deployment, and database consistency straightforward while the product boundaries and workload patterns are still being discovered. Package boundaries can provide separation without introducing network calls, distributed transactions, duplicated operational tooling, or premature service ownership.

The implemented `discord` package is the JDA infrastructure boundary for Gateway configuration, connection lifecycle, guild lifecycle events, and health reporting. The `guild` package owns the persistent guild model, repository, platform-neutral commands, and transactional connection use cases. Other feature packages will be added when their behavior is implemented; there are no empty placeholder modules.

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

## Initial request and database flow

1. An HTTP request reaches the embedded web server and Spring MVC routing.
2. A future feature's application layer coordinates its use case and accesses persistence through that feature's boundary.
3. Spring Data JPA uses the configured PostgreSQL data source and the `guild_os` default schema.
4. PostgreSQL commits the transaction and the web layer returns the result.

At startup, Flyway connects before JPA initialization, ensures the `guild_os` migration schema exists, and applies versioned migrations. Hibernate then validates mapped entities against that schema with `ddl-auto: validate`; it does not create or modify tables. The initial migration creates only the `guild_os` schema. Actuator supplies the standard health endpoint without a custom controller.
