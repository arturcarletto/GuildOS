package io.github.arturcarletto.guildos.platform;

/**
 * Platform-neutral outbound seam: sends a plain-text message to a channel on one platform.
 *
 * <p>Each adapter provides its own implementation ({@code TelegramMessageSender}, and later a Discord
 * one). {@link #platform()} lets a future router pick the correct sender for a target channel without
 * knowing any adapter's SDK types.
 */
public interface PlatformMessageSender {

    CommunityPlatform platform();

    void sendText(PlatformChannelId channel, String text);
}
