package io.github.arturcarletto.guildos.platform;

import java.time.Instant;
import java.util.Objects;

/**
 * A platform-neutral view of something that happened in a community, translated by an adapter from
 * its platform-specific event type.
 *
 * <p>It intentionally carries only privacy-conscious metadata — the platform, the safe ids involved,
 * and when it occurred. It never carries message text, display names, or other content. The optional
 * ids ({@code community}, {@code channel}, {@code actor}, {@code message}) may be {@code null} when a
 * platform does not provide them for a given event, but any id that is present must belong to the
 * same {@link CommunityPlatform} as the event.
 */
public record IncomingCommunityEvent(
        CommunityPlatform platform,
        PlatformCommunityId community,
        PlatformChannelId channel,
        PlatformActorId actor,
        PlatformMessageId message,
        Instant occurredAt) {

    public IncomingCommunityEvent {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        requireSamePlatform(platform, community == null ? null : community.platform(), "community");
        requireSamePlatform(platform, channel == null ? null : channel.platform(), "channel");
        requireSamePlatform(platform, actor == null ? null : actor.platform(), "actor");
        requireSamePlatform(platform, message == null ? null : message.platform(), "message");
    }

    private static void requireSamePlatform(CommunityPlatform expected, CommunityPlatform actual, String field) {
        if (actual != null && actual != expected) {
            throw new IllegalArgumentException(
                    "%s id platform %s does not match event platform %s".formatted(field, actual, expected));
        }
    }
}
