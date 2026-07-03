package io.github.arturcarletto.guildos.guildmembermessage;

/** Raised when a member-message configuration fails platform-neutral validation. */
public final class InvalidMemberMessageConfigurationException extends RuntimeException {

    public InvalidMemberMessageConfigurationException(String message) {
        super(message);
    }
}
