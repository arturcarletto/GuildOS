package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;

/**
 * A fully rendered, platform-neutral member message ready for a Discord adapter to turn into an
 * embed and optional components. It never carries an internal version or database identifier.
 */
public record RenderedMemberMessage(
        MemberMessageKind kind,
        String title,
        String description,
        int accentColor,
        String imageUrl,
        String footer,
        int memberCount,
        boolean mentionMember,
        String memberMention,
        String buttonLabel,
        String buttonUrl) {

    public boolean hasButton() {
        return buttonLabel != null && buttonUrl != null;
    }

    public Optional<String> optionalImageUrl() {
        return Optional.ofNullable(imageUrl);
    }

    /** The plain message content that should accompany the embed, if a member mention is requested. */
    public Optional<String> mentionContent() {
        return mentionMember && memberMention != null && !memberMention.isBlank()
                ? Optional.of(memberMention)
                : Optional.empty();
    }
}
