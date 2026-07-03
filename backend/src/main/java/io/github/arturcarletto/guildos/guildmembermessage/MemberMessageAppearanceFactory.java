package io.github.arturcarletto.guildos.guildmembermessage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and assembles a {@link MemberMessageAppearance} from a {@link ConfigureMemberMessageCommand},
 * applying kind-specific defaults on first creation and preserving existing values on update.
 */
final class MemberMessageAppearanceFactory {

    private static final int EMBED_TOTAL_LIMIT = 6000;
    private static final int RESERVED_FIELD_BUDGET = 64;
    private static final int MAX_URL_LENGTH = 512;
    private static final Pattern HEX_COLOR = Pattern.compile("#?([0-9A-Fa-f]{6})");

    private MemberMessageAppearanceFactory() {
    }

    static MemberMessageAppearance forCreate(
            MemberMessageKind kind, ConfigureMemberMessageCommand command) {
        Defaults defaults = Defaults.of(kind);
        int color = command.color()
                .map(MemberMessageAppearanceFactory::parseColor)
                .orElse(defaults.color());
        return assemble(
                kind,
                command.title().orElse(defaults.title()),
                command.message(),
                color,
                command.imageUrl().orElse(null),
                command.footer().orElse(defaults.footer()),
                command.includeBots().orElse(defaults.includeBots()),
                resolveMentionMember(kind, command, defaults.mentionMember()),
                command.buttonLabel().orElse(null),
                command.buttonUrl().orElse(null));
    }

    static MemberMessageAppearance forUpdate(
            MemberMessageKind kind,
            ConfigureMemberMessageCommand command,
            MemberMessageAppearance existing) {
        int color = command.color()
                .map(MemberMessageAppearanceFactory::parseColor)
                .orElse(existing.accentColor());
        return assemble(
                kind,
                command.title().orElse(existing.title()),
                command.message(),
                color,
                command.imageUrl().orElse(existing.imageUrl()),
                command.footer().orElse(existing.footer()),
                command.includeBots().orElse(existing.includeBots()),
                resolveMentionMember(kind, command, existing.mentionMember()),
                command.buttonLabel().orElse(existing.buttonLabel()),
                command.buttonUrl().orElse(existing.buttonUrl()));
    }

    private static MemberMessageAppearance assemble(
            MemberMessageKind kind,
            String rawTitle,
            String rawDescription,
            int accentColor,
            String rawImageUrl,
            String rawFooter,
            boolean includeBots,
            boolean mentionMember,
            String rawButtonLabel,
            String rawButtonUrl) {
        if (!kind.supportsButton() && (rawButtonLabel != null || rawButtonUrl != null)) {
            throw new InvalidMemberMessageConfigurationException(
                    "Goodbye messages do not support a button");
        }
        String title = MemberMessageTemplate.parse(rawTitle, MemberMessageField.TITLE, kind).value();
        String description =
                MemberMessageTemplate.parse(rawDescription, MemberMessageField.DESCRIPTION, kind).value();
        String footer =
                MemberMessageTemplate.parse(rawFooter, MemberMessageField.FOOTER, kind).value();
        String imageUrl = rawImageUrl == null ? null : validateHttpsUrl(rawImageUrl, "image URL");

        String buttonLabel = null;
        String buttonUrl = null;
        if (rawButtonLabel != null || rawButtonUrl != null) {
            if (rawButtonLabel == null || rawButtonUrl == null) {
                throw new InvalidMemberMessageConfigurationException(
                        "A button needs both a label and an HTTPS URL");
            }
            buttonLabel =
                    MemberMessageTemplate.parse(rawButtonLabel, MemberMessageField.BUTTON_LABEL, kind).value();
            buttonUrl = validateHttpsUrl(rawButtonUrl, "button URL");
        }

        int combined = MemberMessageTemplate.renderedLength(title)
                + MemberMessageTemplate.renderedLength(description)
                + MemberMessageTemplate.renderedLength(footer)
                + RESERVED_FIELD_BUDGET;
        if (combined > EMBED_TOTAL_LIMIT) {
            throw new InvalidMemberMessageConfigurationException(
                    "The combined title, description and footer could exceed Discord's embed limit");
        }

        return new MemberMessageAppearance(
                title,
                description,
                accentColor,
                imageUrl,
                footer,
                mentionMember,
                includeBots,
                buttonLabel,
                buttonUrl);
    }

    private static boolean resolveMentionMember(
            MemberMessageKind kind, ConfigureMemberMessageCommand command, boolean fallback) {
        if (!kind.supportsMemberMention()) {
            if (command.mentionMember().orElse(false)) {
                throw new InvalidMemberMessageConfigurationException(
                        "Goodbye messages cannot mention the member");
            }
            return false;
        }
        return command.mentionMember().orElse(fallback);
    }

    private static int parseColor(String rawColor) {
        Matcher matcher = HEX_COLOR.matcher(rawColor.strip());
        if (!matcher.matches()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The color must be a hexadecimal value such as #57F287");
        }
        return Integer.parseInt(matcher.group(1), 16);
    }

    private static String validateHttpsUrl(String rawUrl, String label) {
        String url = rawUrl.strip();
        if (url.length() > MAX_URL_LENGTH) {
            throw new InvalidMemberMessageConfigurationException(
                    "The " + label + " is too long");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new InvalidMemberMessageConfigurationException("The " + label + " is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidMemberMessageConfigurationException("The " + label + " must be an HTTPS URL");
        }
        return url;
    }

    private record Defaults(
            String title, int color, String footer, boolean mentionMember, boolean includeBots) {

        static Defaults of(MemberMessageKind kind) {
            return switch (kind) {
                case WELCOME -> new Defaults(
                        "Welcome to {server}!", 0x57F287, "Welcome • {server}", true, false);
                case GOODBYE -> new Defaults(
                        "A member has left", 0xED4245, "Goodbye • {server}", false, false);
            };
        }
    }
}
