package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, non-recursive placeholder rendering shared by welcome and goodbye messages. */
final class MemberMessageTemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{member}|\\{username}|\\{server}|\\{memberCount}|\\{mention}");

    private MemberMessageTemplateRenderer() {
    }

    static RenderedMemberMessage renderMessage(
            MemberMessageKind kind,
            MemberMessageAppearance appearance,
            MemberMessageRenderContext context) {
        String buttonLabel = appearance.buttonLabel() == null
                ? null
                : render(appearance.buttonLabel(), context);
        return new RenderedMemberMessage(
                kind,
                render(appearance.title(), context),
                render(appearance.description(), context),
                appearance.accentColor(),
                appearance.imageUrl(),
                render(appearance.footer(), context),
                context.memberCount(),
                appearance.mentionMember(),
                appearance.mentionMember() ? context.memberMention() : "",
                buttonLabel,
                appearance.buttonUrl());
    }

    static String render(String template, MemberMessageRenderContext context) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String replacement = switch (matcher.group()) {
                case "{member}" -> context.memberDisplayName();
                case "{username}" -> context.username();
                case "{server}" -> context.serverName();
                case "{memberCount}" -> Integer.toString(context.memberCount());
                case "{mention}" -> context.memberMention();
                default -> throw new IllegalStateException("Unsupported member message placeholder");
            };
            // quoteReplacement keeps replacement values literal, so they are never re-expanded.
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
