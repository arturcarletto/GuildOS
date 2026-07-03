package io.github.arturcarletto.guildos.guildaccess;

import java.util.regex.Pattern;

/**
 * Validates Discord snowflake path parameters as decimal strings of 1-20 digits.
 */
final class DiscordSnowflakes {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");

    private DiscordSnowflakes() {
    }

    static String requireValid(String discordGuildId) {
        if (discordGuildId == null || !SNOWFLAKE.matcher(discordGuildId).matches()) {
            throw new InvalidDiscordGuildIdException("guildId must be a Discord snowflake");
        }
        return discordGuildId;
    }
}
