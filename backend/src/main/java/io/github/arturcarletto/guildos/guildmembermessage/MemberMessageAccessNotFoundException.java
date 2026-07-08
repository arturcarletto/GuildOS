package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * Raised when the operator has no active authorization for the guild, or the guild is unknown.
 * Deliberately non-enumerating: a missing guild and a missing/revoked authorization map to the same
 * {@code 404} so it cannot be used to discover another operator's guilds.
 */
class MemberMessageAccessNotFoundException extends RuntimeException {
}
