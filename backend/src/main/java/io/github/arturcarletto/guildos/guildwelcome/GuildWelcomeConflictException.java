package io.github.arturcarletto.guildos.guildwelcome;

public final class GuildWelcomeConflictException extends RuntimeException {

    public GuildWelcomeConflictException() {
        super("The welcome configuration changed concurrently");
    }
}
