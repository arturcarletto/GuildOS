package io.github.arturcarletto.guildos.guildwelcome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WelcomeTemplateRenderer {

    static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final int ADMINISTRATIVE_PREVIEW_LIMIT = DISCORD_MESSAGE_LIMIT - 400;

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{member}|\\{server}|\\{memberCount}");

    private WelcomeTemplateRenderer() {
    }

    static String render(String template, WelcomePreviewContext context) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String replacement = switch (matcher.group()) {
                case "{member}" -> context.memberDisplayName();
                case "{server}" -> context.serverName();
                case "{memberCount}" -> Integer.toString(context.memberCount());
                default -> throw new IllegalStateException("Unsupported welcome placeholder");
            };
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        if (rendered.length() > ADMINISTRATIVE_PREVIEW_LIMIT) {
            throw new InvalidWelcomeTemplateException(
                    "The rendered welcome message exceeds Discord's message limit");
        }
        return rendered.toString();
    }
}
