package io.github.arturcarletto.guildos.guildwelcome;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record WelcomeTemplate(String value) {

    static final int MAX_TEMPLATE_LENGTH = 1000;
    private static final Pattern PLACEHOLDER_LIKE = Pattern.compile("\\{[^{}]*}");
    private static final Pattern MASS_MENTION =
            Pattern.compile("@(everyone|here)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_DISCORD_MENTION =
            Pattern.compile("<(?:@!?|@&|#)[0-9]{1,20}>");
    private static final Set<String> ALLOWED_PLACEHOLDERS =
            Set.of("{member}", "{server}", "{memberCount}");
    private static final WelcomePreviewContext MAXIMUM_CONTEXT =
            new WelcomePreviewContext("M".repeat(32), "S".repeat(100), Integer.MAX_VALUE);

    static WelcomeTemplate parse(String rawTemplate) {
        if (rawTemplate == null) {
            throw new InvalidWelcomeTemplateException("A welcome message is required");
        }
        String normalized = rawTemplate.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isBlank()) {
            throw new InvalidWelcomeTemplateException("The welcome message must not be blank");
        }
        if (normalized.length() > MAX_TEMPLATE_LENGTH) {
            throw new InvalidWelcomeTemplateException(
                    "The welcome message must not exceed 1000 characters");
        }
        rejectUnsupportedPlaceholders(normalized);
        if (MASS_MENTION.matcher(normalized).find()
                || RAW_DISCORD_MENTION.matcher(normalized).find()) {
            throw new InvalidWelcomeTemplateException(
                    "The welcome message must not contain Discord mentions");
        }
        WelcomeTemplateRenderer.render(normalized, MAXIMUM_CONTEXT);
        return new WelcomeTemplate(normalized);
    }

    private static void rejectUnsupportedPlaceholders(String value) {
        Matcher matcher = PLACEHOLDER_LIKE.matcher(value);
        StringBuilder remainder = new StringBuilder();
        while (matcher.find()) {
            if (!ALLOWED_PLACEHOLDERS.contains(matcher.group())) {
                throw new InvalidWelcomeTemplateException(
                        "The welcome message contains an unsupported placeholder");
            }
            matcher.appendReplacement(remainder, "");
        }
        matcher.appendTail(remainder);
        if (remainder.indexOf("{") >= 0 || remainder.indexOf("}") >= 0) {
            throw new InvalidWelcomeTemplateException(
                    "The welcome message contains an unsupported placeholder");
        }
    }
}
