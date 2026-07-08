package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * Raised when a toggle is attempted for a kind that has never been configured. Toggle must never
 * create a configuration, so the caller is asked to configure it first.
 */
class MemberMessageNotConfiguredException extends RuntimeException {
}
