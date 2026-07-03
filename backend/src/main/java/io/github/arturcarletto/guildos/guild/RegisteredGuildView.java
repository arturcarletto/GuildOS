package io.github.arturcarletto.guildos.guild;

import java.util.UUID;

/**
 * Safe, application-facing projection of a registered guild for other capabilities.
 *
 * <p>{@code registeredGuildId} is the internal registry UUID (the persistent primary key) and
 * {@code discordGuildId} is the Discord snowflake. {@code connected} is {@code true} only while the
 * bot's Gateway connection to the guild is currently live.
 */
public record RegisteredGuildView(
        UUID registeredGuildId,
        String discordGuildId,
        String name,
        boolean connected) {
}
