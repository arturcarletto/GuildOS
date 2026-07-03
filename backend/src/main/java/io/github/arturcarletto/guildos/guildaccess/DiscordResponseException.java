package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when Discord returns a malformed successful response, a missing required field, or an
 * unexpected client error that Guild OS cannot act on. Carries no token or Discord response body.
 */
class DiscordResponseException extends RuntimeException {

    DiscordResponseException(String message) {
        super(message);
    }
}
