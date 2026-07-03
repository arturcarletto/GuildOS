package io.github.arturcarletto.guildos.guildaccess;

/**
 * Raised when the authenticated operator is not eligible (per current Discord data) to onboard the
 * requested guild. Maps to HTTP 403.
 */
class OperatorGuildEligibilityException extends RuntimeException {

    OperatorGuildEligibilityException(String message) {
        super(message);
    }
}
