# ADR 0002: Platform adapter architecture and the Telegram proof of concept

## Status

Accepted

## Context

Guild OS began as a Discord-first community-management backend. The domain — activity ingestion, analytics, onboarding, settings, and operator/dashboard APIs — is not fundamentally Discord-specific; it is community management that currently happens to run on Discord. There is now interest in supporting additional chat platforms, starting with Telegram.

Two failure modes must be avoided. The first is prematurely splitting Guild OS into separate bot projects or repositories, which would multiply deployment, persistence, and operational surfaces before any second platform has proven its value. The second is a large, risky rename of the existing `guild` concept into a generic `community` concept across persistence, APIs, and the frontend, which would touch stable, well-tested code for no immediate functional gain.

## Decision

Keep Guild OS as one deployable modular monolith (see [ADR 0001](0001-modular-monolith.md)) and model each chat platform as an **adapter** inside it, behind a small platform-neutral seam.

- Introduce a new `io.github.arturcarletto.guildos.platform` package with minimal, immutable value objects: `CommunityPlatform` (`DISCORD`, `TELEGRAM`), the platform-scoped ids (`PlatformCommunityId`, `PlatformActorId`, `PlatformChannelId`, `PlatformMessageId`), a privacy-conscious `IncomingCommunityEvent`, a `PlatformBotCommand`, and a `PlatformMessageSender` outbound seam. These carry only safe ids and metadata — never message text or display names — and depend on no adapter SDK.
- The existing Discord adapter (`discord` package, JDA) is left unchanged. Its behavior, persistence, and APIs are preserved.
- Add a new `telegram` adapter package as an intentionally small proof of concept: configuration properties, a minimal Telegram Bot API client (`getUpdates`/`sendMessage`) built on Spring's `RestClient`, an in-memory long-polling loop, and a single `/ping` command that replies "GuildOS is online." The adapter maps Telegram updates into the neutral `IncomingCommunityEvent`/`PlatformMessageSender` seam rather than exposing Telegram types outward.
- Telegram is disabled by default. When enabled it requires a bot token and fails fast otherwise; the token is never serialized, logged, or included in `toString`.

Defer everything not needed to prove the seam: Telegram onboarding, OAuth, dashboard support, welcome/goodbye, moderation, and activity persistence. Defer the `guild`→`community` rename until a second platform genuinely needs shared persistence.

## Consequences

- Guild OS visibly becomes "core + platform adapters" without a second repository, a second service, or a new runtime.
- New platform code depends on the neutral `platform` package; adapter SDK types (JDA, Telegram wire DTOs) stay inside their adapters. An ArchUnit test enforces that `platform` stays adapter-neutral.
- Existing Discord behavior and tests are untouched, so the change is low risk.
- Telegram is a real, runnable demonstration but not yet a product: it does not persist activity, onboard chats, or authenticate operators.
- A future guild→community unification, and a Telegram activity-ingestion bridge, remain deliberate follow-ups with their own migration planning rather than being forced now.

## Alternatives considered

- **A separate Telegram bot project/repository:** rejected — it would duplicate deployment, persistence, and operational tooling before Telegram has proven its value, and contradicts [ADR 0001](0001-modular-monolith.md).
- **Immediate `guild`→`community` rename across the codebase:** rejected — a high-risk change to stable, well-tested Discord persistence and APIs with no immediate functional benefit. Discord guilds remain the first supported community type; the rename can happen later when a second platform needs shared persistence.
- **A heavyweight third-party Telegram framework:** rejected — the proof of concept needs only `getUpdates` and `sendMessage`, which a small `RestClient`-based client covers in the project's existing HTTP style without a new dependency.
- **Webhook-based Telegram updates:** deferred — long polling is simpler for local/dev proof of concept and needs no public HTTPS endpoint; webhooks can be revisited if Telegram graduates beyond a proof of concept.
