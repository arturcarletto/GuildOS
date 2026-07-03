package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when the operator's Discord authorization is missing or rejected (Discord 401/403), so the
 * operator must re-authenticate. Carries no token or Discord response body.
 */
class DiscordReauthenticationRequiredException extends RuntimeException {

    DiscordReauthenticationRequiredException(String message) {
        super(message);
    }
}
