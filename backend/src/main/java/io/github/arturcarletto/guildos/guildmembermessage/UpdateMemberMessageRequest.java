package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dashboard request body to create or replace a welcome/goodbye configuration.
 *
 * <p>{@code channelId} and {@code message} are always required. Every other field is optional; a
 * {@code null} or blank value is treated as "not supplied", which — matching the slash-command
 * semantics — preserves the existing value on an update and falls back to the kind's default on a
 * first configuration. Clearing an already-set optional field is intentionally not supported in this
 * branch, consistent with {@code /welcome configure} and {@code /goodbye configure}.
 *
 * <p>The welcome-only fields ({@code mentionMember}, {@code buttonLabel}, {@code buttonUrl}) are
 * rejected by the domain validation when supplied for a goodbye configuration.
 */
public record UpdateMemberMessageRequest(
        @NotBlank @Size(max = 20) String channelId,
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 256) String title,
        @Size(max = 32) String color,
        @Size(max = 512) String imageUrl,
        @Size(max = 2048) String footer,
        Boolean includeBots,
        Boolean mentionMember,
        @Size(max = 80) String buttonLabel,
        @Size(max = 512) String buttonUrl) {

    ConfigureMemberMessageCommand toCommand() {
        return new ConfigureMemberMessageCommand(
                channelId == null ? null : channelId.trim(),
                message,
                optional(title),
                optional(color),
                optional(imageUrl),
                optional(footer),
                Optional.ofNullable(includeBots),
                Optional.ofNullable(mentionMember),
                optional(buttonLabel),
                optional(buttonUrl));
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
