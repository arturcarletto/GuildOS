package io.github.arturcarletto.guildos.guildmembermessage;

/** Raised when a member-message configuration changed concurrently and the command must retry. */
public final class GuildMemberMessageConflictException extends RuntimeException {

    public GuildMemberMessageConflictException() {
        super("The member message configuration changed concurrently");
    }
}
