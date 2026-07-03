package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when the requested guild is unknown to Guild OS or the bot is not currently connected to it.
 * Maps to HTTP 404.
 */
class GuildNotOnboardableException extends RuntimeException {

    GuildNotOnboardableException(String message) {
        super(message);
    }
}
