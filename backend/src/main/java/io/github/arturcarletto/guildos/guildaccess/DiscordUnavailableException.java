package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when Discord cannot currently serve the request (rate limiting, a 5xx response, a timeout,
 * or a network failure). Carries no token or Discord response body.
 */
class DiscordUnavailableException extends RuntimeException {

    DiscordUnavailableException(String message) {
        super(message);
    }
}
