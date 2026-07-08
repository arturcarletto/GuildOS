package io.github.arturcarletto.guildos.platform;

/**
 * A platform-scoped identifier for a channel/conversation where a message is read or sent (a Discord
 * channel, a Telegram chat). Validated non-blank and trimmed for consistent equality.
 */
public record PlatformChannelId(CommunityPlatform platform, String externalId) {

    public PlatformChannelId {
        externalId = PlatformIds.requireValid(platform, externalId);
    }

    public static PlatformChannelId of(CommunityPlatform platform, String externalId) {
        return new PlatformChannelId(platform, externalId);
    }
}
