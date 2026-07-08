package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record TimeoutMemberCommand(
        String discordGuildId,
        String targetUserId,
        Duration duration,
        Optional<String> reason) {

    public TimeoutMemberCommand {
        discordGuildId = requirePresent(discordGuildId, "discordGuildId");
        targetUserId = requirePresent(targetUserId, "targetUserId");
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        reason = reason == null ? Optional.empty() : reason.map(String::trim).filter(value -> !value.isBlank());
    }

    private static String requirePresent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be present");
        }
        return value.trim();
    }
}
