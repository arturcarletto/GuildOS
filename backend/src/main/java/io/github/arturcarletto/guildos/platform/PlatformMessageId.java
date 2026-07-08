package io.github.arturcarletto.guildos.platform;

/**
 * A platform-scoped identifier for a single message. Validated non-blank and trimmed for consistent
 * equality. It identifies a message; it never carries the message's text.
 */
public record PlatformMessageId(CommunityPlatform platform, String externalId) {

    public PlatformMessageId {
        externalId = PlatformIds.requireValid(platform, externalId);
    }

    public static PlatformMessageId of(CommunityPlatform platform, String externalId) {
        return new PlatformMessageId(platform, externalId);
    }
}
