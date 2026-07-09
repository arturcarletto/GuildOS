package io.github.arturcarletto.guildos.guildmoderation;

/**
 * A single member returned by a live moderation search.
 *
 * <p>These fields are transient selection metadata resolved from Discord at request time. Guild OS
 * does not persist usernames, display names, or avatars for this feature. {@code username} or
 * {@code displayName} may be {@code null} when Discord cannot safely provide them.
 */
public record MemberSearchResultMember(
        String userId,
        String username,
        String displayName,
        boolean bot) {
}
