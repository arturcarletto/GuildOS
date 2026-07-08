package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TimeoutMemberRequest(
        @Size(max = 20) String targetUserId,
        @NotNull @Min(1) @Max(40_320) Integer durationMinutes,
        @Size(max = 240) String reason) {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");

    TimeoutMemberCommand toCommand(String discordGuildId) {
        String normalizedTargetUserId = requireSnowflake(targetUserId);
        return new TimeoutMemberCommand(
                discordGuildId,
                normalizedTargetUserId,
                Duration.ofMinutes(durationMinutes.longValue()),
                normalizeReason(reason));
    }

    private static String requireSnowflake(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!SNOWFLAKE.matcher(normalized).matches()) {
            throw new InvalidModerationActionException(
                    "Target user id must be a Discord snowflake.");
        }
        return normalized;
    }

    private static Optional<String> normalizeReason(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new InvalidModerationActionException("Reason cannot be blank when provided.");
        }
        if (normalized.length() > 240) {
            throw new InvalidModerationActionException("Reason must be 240 characters or fewer.");
        }
        return Optional.of(normalized);
    }
}
