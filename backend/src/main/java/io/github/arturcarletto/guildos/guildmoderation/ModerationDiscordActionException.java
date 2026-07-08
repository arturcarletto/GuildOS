package io.github.arturcarletto.guildos.guildmoderation;

import java.util.Objects;

public class ModerationDiscordActionException extends RuntimeException {

    private final ModerationFailureCategory category;

    public ModerationDiscordActionException(ModerationFailureCategory category) {
        super("Discord moderation action failed: " + category.name());
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public ModerationFailureCategory category() {
        return category;
    }
}
