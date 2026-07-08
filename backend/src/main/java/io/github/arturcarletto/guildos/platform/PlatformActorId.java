package io.github.arturcarletto.guildos.platform;

/**
 * A platform-scoped identifier for an actor (the user who triggered an event). Validated non-blank
 * and trimmed for consistent equality. Never a display name — only the platform's stable id.
 */
public record PlatformActorId(CommunityPlatform platform, String externalId) {

    public PlatformActorId {
        externalId = PlatformIds.requireValid(platform, externalId);
    }

    public static PlatformActorId of(CommunityPlatform platform, String externalId) {
        return new PlatformActorId(platform, externalId);
    }
}
