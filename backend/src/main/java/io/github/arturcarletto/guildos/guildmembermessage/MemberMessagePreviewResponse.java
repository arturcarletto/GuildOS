package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * Safe, rendered dashboard preview of a member message. It is produced from deterministic sample
 * values and never sent to Discord. It carries only what a preview card needs; welcome-only button
 * fields are {@code null} for goodbye.
 */
public record MemberMessagePreviewResponse(
        String kind,
        String title,
        String description,
        String color,
        String imageUrl,
        String footer,
        int memberCount,
        boolean mentionMember,
        String buttonLabel,
        String buttonUrl) {

    static MemberMessagePreviewResponse from(MemberMessageKind kind, RenderedMemberMessage rendered) {
        return new MemberMessagePreviewResponse(
                kind.name(),
                rendered.title(),
                rendered.description(),
                MemberMessageColors.toHex(rendered.accentColor()),
                rendered.imageUrl(),
                rendered.footer(),
                rendered.memberCount(),
                rendered.mentionMember(),
                rendered.buttonLabel(),
                rendered.buttonUrl());
    }
}
