package io.github.arturcarletto.guildos.guildmoderation;

import java.util.List;

/**
 * Adapter-facing result of a live member search. Wraps only safe selection metadata; the adapter is
 * responsible for keeping JDA types and raw Discord payloads out of this model.
 */
public record MemberSearchResult(List<MemberSearchResultMember> members) {

    public MemberSearchResult {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static MemberSearchResult empty() {
        return new MemberSearchResult(List.of());
    }
}
