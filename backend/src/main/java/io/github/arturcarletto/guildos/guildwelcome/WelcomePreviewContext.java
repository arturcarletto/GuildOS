package io.github.arturcarletto.guildos.guildwelcome;

import java.util.Objects;

public record WelcomePreviewContext(
        String memberDisplayName,
        String serverName,
        int memberCount) {

    public WelcomePreviewContext {
        Objects.requireNonNull(memberDisplayName, "memberDisplayName must not be null");
        Objects.requireNonNull(serverName, "serverName must not be null");
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount must not be negative");
        }
    }
}
