package io.github.arturcarletto.guildos.platform;

/**
 * The chat platforms GuildOS can manage a community on.
 *
 * <p>Discord is the first complete adapter; Telegram is an experimental proof-of-concept adapter.
 * This enum is the small, stable seam that lets platform-neutral GuildOS code refer to "which
 * platform" without depending on any adapter's SDK types.
 */
public enum CommunityPlatform {
    DISCORD,
    TELEGRAM
}
