package io.github.arturcarletto.guildos.guildaccess;

import java.util.UUID;

/**
 * Safe application-facing projection of an operator's active authorization for a registered guild.
 * It exposes no persistence entity, OAuth credential, Discord permission bitset, or operator id.
 */
public record AuthorizedGuildAccess(
        UUID registeredGuildId,
        String discordGuildId,
        String name,
        String role,
        boolean connected) {
}
