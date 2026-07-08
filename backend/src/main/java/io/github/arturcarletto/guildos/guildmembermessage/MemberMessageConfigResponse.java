package io.github.arturcarletto.guildos.guildmembermessage;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Safe dashboard projection of a member-message configuration. It exposes only appearance and
 * enablement fields — never the internal optimistic-locking version, database ids, or channel
 * metadata beyond the raw channel id already stored. Welcome-only fields ({@code mentionMember},
 * {@code buttonLabel}, {@code buttonUrl}) are {@code null} for goodbye.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberMessageConfigResponse(
        String kind,
        boolean configured,
        boolean enabled,
        String channelId,
        String title,
        String message,
        String color,
        String imageUrl,
        String footer,
        boolean includeBots,
        Boolean mentionMember,
        String buttonLabel,
        String buttonUrl) {

    static MemberMessageConfigResponse notConfigured(MemberMessageKind kind) {
        return new MemberMessageConfigResponse(
                kind.name(), false, false, null, null, null, null, null, null, false, null, null, null);
    }

    static MemberMessageConfigResponse configured(StoredGuildMemberMessage stored) {
        MemberMessageAppearance appearance = stored.appearance();
        return new MemberMessageConfigResponse(
                stored.kind().name(),
                true,
                stored.enabled(),
                stored.channelId(),
                appearance.title(),
                appearance.description(),
                MemberMessageColors.toHex(appearance.accentColor()),
                appearance.imageUrl(),
                appearance.footer(),
                appearance.includeBots(),
                stored.kind().supportsMemberMention() ? appearance.mentionMember() : null,
                appearance.buttonLabel(),
                appearance.buttonUrl());
    }
}
