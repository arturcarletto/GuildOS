package io.github.arturcarletto.guildos.guildaccess;

/**
 * Safe view of a guild the operator is currently authorized to manage. Exposes no OAuth token,
 * session identifier, or internal persistence field.
 *
 * <p>{@code guildId} is the Discord snowflake; {@code role} is the Guild OS role
 * ({@code OWNER}/{@code ADMIN}).
 */
public record AuthorizedGuildResponse(
        String guildId,
        String name,
        String role) {
}
