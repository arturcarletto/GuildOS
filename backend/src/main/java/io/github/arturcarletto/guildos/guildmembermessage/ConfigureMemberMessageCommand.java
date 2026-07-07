package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Objects;
import java.util.Optional;

/**
 * Platform-neutral request to create or update a member-message configuration.
 *
 * <p>{@code channelId} and {@code message} (the description template) are always supplied. Every
 * other value is optional: on an update an empty optional preserves the existing value; on the
 * first configuration an empty optional falls back to the kind's default.
 */
public record ConfigureMemberMessageCommand(
        String channelId,
        String message,
        Optional<String> title,
        Optional<String> color,
        Optional<String> imageUrl,
        Optional<String> footer,
        Optional<Boolean> includeBots,
        Optional<Boolean> mentionMember,
        Optional<String> buttonLabel,
        Optional<String> buttonUrl) {

    public ConfigureMemberMessageCommand {
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(imageUrl, "imageUrl must not be null");
        Objects.requireNonNull(footer, "footer must not be null");
        Objects.requireNonNull(includeBots, "includeBots must not be null");
        Objects.requireNonNull(mentionMember, "mentionMember must not be null");
        Objects.requireNonNull(buttonLabel, "buttonLabel must not be null");
        Objects.requireNonNull(buttonUrl, "buttonUrl must not be null");
    }
}
