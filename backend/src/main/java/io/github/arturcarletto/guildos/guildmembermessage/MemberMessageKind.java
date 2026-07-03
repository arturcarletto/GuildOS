package io.github.arturcarletto.guildos.guildmembermessage;

/** The two member lifecycle events Guild OS can announce with a configured rich message. */
public enum MemberMessageKind {
    WELCOME,
    GOODBYE;

    /** Whether this kind may mention the affected member and use the {@code {mention}} placeholder. */
    public boolean supportsMemberMention() {
        return this == WELCOME;
    }

    /** Whether this kind may attach a link button to its public message. */
    public boolean supportsButton() {
        return this == WELCOME;
    }
}
