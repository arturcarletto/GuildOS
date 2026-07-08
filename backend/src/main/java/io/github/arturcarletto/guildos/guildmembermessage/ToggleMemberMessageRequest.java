package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;

public record ToggleMemberMessageRequest(Boolean enabled) {

    Optional<Boolean> targetEnabled() {
        return Optional.ofNullable(enabled);
    }
}
