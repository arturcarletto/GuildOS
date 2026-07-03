package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;

/**
 * Validated, platform-neutral appearance of a member message. All template fields are normalized;
 * {@code accentColor} is a parsed 24-bit RGB value; URLs, when present, are validated HTTPS.
 * Nullable fields ({@code imageUrl}, {@code buttonLabel}, {@code buttonUrl}) are absent when unset.
 */
public record MemberMessageAppearance(
        String title,
        String description,
        int accentColor,
        String imageUrl,
        String footer,
        boolean mentionMember,
        boolean includeBots,
        String buttonLabel,
        String buttonUrl) {

    public Optional<String> optionalImageUrl() {
        return Optional.ofNullable(imageUrl);
    }

    public boolean hasButton() {
        return buttonLabel != null && buttonUrl != null;
    }
}
