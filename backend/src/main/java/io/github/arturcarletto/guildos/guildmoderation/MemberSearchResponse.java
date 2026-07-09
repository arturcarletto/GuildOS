package io.github.arturcarletto.guildos.guildmoderation;

import java.util.List;

/**
 * Safe HTTP response for a live moderation member search. Exposes only fields useful for selecting a
 * moderation target and never internal UUIDs, authorization records, OAuth/session data, tokens,
 * roles, or permission bitsets.
 */
public record MemberSearchResponse(
        String guildId,
        String query,
        int limit,
        List<MemberSearchResultMember> results) {

    static MemberSearchResponse of(String discordGuildId, MemberSearchQuery query, MemberSearchResult result) {
        return new MemberSearchResponse(
                discordGuildId,
                query.query(),
                query.limit(),
                result.members());
    }
}
