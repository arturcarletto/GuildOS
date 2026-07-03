package io.github.arturcarletto.guildos.guildaccess;

import java.util.List;

/**
 * Retrieves the authenticated operator's Discord guilds. Implementations use the operator's OAuth
 * access token as a bearer credential and never persist, log, or expose it.
 */
interface DiscordGuildClient {

    List<OperatorDiscordGuild> fetchOperatorGuilds(String accessToken);
}
