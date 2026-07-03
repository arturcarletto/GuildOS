package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;

/**
 * A guild returned by Discord for the authenticated operator, mapped into the guildaccess boundary.
 *
 * <p>This is a Guild OS adapter model, never a Discord SDK/OAuth type, and it is never persisted or
 * exposed through the API. It carries no OAuth token.
 */
record OperatorDiscordGuild(
        String discordGuildId,
        String name,
        String iconHash,
        boolean owner,
        BigInteger permissions) {
}
