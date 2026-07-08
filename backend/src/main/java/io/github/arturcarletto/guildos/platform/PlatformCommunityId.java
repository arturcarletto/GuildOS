package io.github.arturcarletto.guildos.platform;

/**
 * A platform-scoped identifier for a community (a Discord guild, a Telegram chat, ...).
 *
 * <p>The {@code externalId} is the raw identifier as the platform reports it (a Discord snowflake,
 * a Telegram chat id). It is validated non-blank and trimmed so equality is consistent.
 */
public record PlatformCommunityId(CommunityPlatform platform, String externalId) {

    public PlatformCommunityId {
        externalId = PlatformIds.requireValid(platform, externalId);
    }

    public static PlatformCommunityId of(CommunityPlatform platform, String externalId) {
        return new PlatformCommunityId(platform, externalId);
    }
}
