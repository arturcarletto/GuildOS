package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when a path guild identifier is not a valid Discord snowflake (a decimal string of 1-20
 * digits). Maps to HTTP 400.
 */
class InvalidDiscordGuildIdException extends RuntimeException {

    InvalidDiscordGuildIdException(String message) {
        super(message);
    }
}
