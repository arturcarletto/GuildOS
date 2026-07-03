package io.github.arturcarletto.guildos.guildaccess;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wire model for a single entry of Discord's {@code GET /users/@me/guilds} response. Only the fields
 * relevant to onboarding are bound; all other fields Discord may add are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record DiscordGuildResponse(
        String id,
        String name,
        String icon,
        Boolean owner,
        String permissions) {
}
