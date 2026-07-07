package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A validated, normalized member-message field template. Validation guarantees every accepted
 * template renders within its field's Discord limit for every valid {@link MemberMessageRenderContext}.
 */
record MemberMessageTemplate(String value) {

    private static final Pattern PLACEHOLDER_LIKE = Pattern.compile("\\{[^{}]*}");
    private static final Pattern MASS_MENTION =
            Pattern.compile("@(everyone|here)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAW_DISCORD_MENTION =
            Pattern.compile("<(?:@!?|@&|#)[0-9]{1,20}>");
    private static final Set<String> COMMON_PLACEHOLDERS =
            Set.of("{member}", "{username}", "{server}", "{memberCount}");
    private static final String MENTION_PLACEHOLDER = "{mention}";
    private static final MemberMessageRenderContext MAXIMUM_CONTEXT =
            MemberMessageRenderContext.maximum();

    static MemberMessageTemplate parse(
            String rawTemplate, MemberMessageField field, MemberMessageKind kind) {
        if (rawTemplate == null) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + field.label() + " is required");
        }
        String normalized = rawTemplate.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isBlank()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + field.label() + " must not be blank");
        }
        if (normalized.length() > field.maxStoredLength()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + field.label() + " must not exceed "
                            + field.maxStoredLength() + " characters");
        }
        boolean allowMention = field.supportsMention() && kind.supportsMemberMention();
        rejectUnsupportedPlaceholders(normalized, field, allowMention);
        if (MASS_MENTION.matcher(normalized).find()
                || RAW_DISCORD_MENTION.matcher(normalized).find()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + field.label() + " must not contain Discord mentions");
        }
        if (renderedLength(normalized) > field.maxRenderedLength()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The rendered " + field.label() + " could exceed Discord's " + field.label()
                            + " limit");
        }
        return new MemberMessageTemplate(normalized);
    }

    /** Worst-case rendered length of an already-validated template under the maximum context. */
    static int renderedLength(String template) {
        return MemberMessageTemplateRenderer.render(template, MAXIMUM_CONTEXT).length();
    }

    private static void rejectUnsupportedPlaceholders(
            String value, MemberMessageField field, boolean allowMention) {
        Set<String> allowed = new LinkedHashSet<>(COMMON_PLACEHOLDERS);
        if (allowMention) {
            allowed.add(MENTION_PLACEHOLDER);
        }
        Matcher matcher = PLACEHOLDER_LIKE.matcher(value);
        StringBuilder remainder = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            if (!allowed.contains(token)) {
                if (MENTION_PLACEHOLDER.equals(token)) {
                    throw new InvalidMemberMessageConfigurationException(
                            "The " + field.label() + " does not support the {mention} placeholder");
                }
                throw new InvalidMemberMessageConfigurationException(
                        "The " + field.label() + " contains an unsupported placeholder");
            }
            matcher.appendReplacement(remainder, "");
        }
        matcher.appendTail(remainder);
        if (remainder.indexOf("{") >= 0 || remainder.indexOf("}") >= 0) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + field.label() + " contains a malformed placeholder");
        }
    }
}
