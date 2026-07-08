package io.github.arturcarletto.guildos.platform;

import java.util.Locale;

/**
 * A platform-neutral definition of a bot command, such as {@code /ping}.
 *
 * <p>The {@code name} is stored normalized: lower-cased and without a leading slash. {@link #matches}
 * recognizes the common ways a chat platform delivers a command in a message — a leading slash, an
 * optional {@code @botname} suffix (as Telegram uses in groups), and trailing arguments.
 */
public record PlatformBotCommand(String name, String description) {

    public PlatformBotCommand {
        name = normalizeName(name);
        description = description == null ? "" : description.trim();
    }

    public static PlatformBotCommand of(String name, String description) {
        return new PlatformBotCommand(name, description);
    }

    /**
     * True when {@code rawText} is an invocation of this command, e.g. {@code "/ping"},
     * {@code "/ping@MyBot"}, or {@code "/ping now"}.
     */
    public boolean matches(String rawText) {
        if (rawText == null) {
            return false;
        }
        String trimmed = rawText.strip();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        String firstToken = trimmed.split("\\s+", 2)[0].substring(1);
        int at = firstToken.indexOf('@');
        String command = at >= 0 ? firstToken.substring(0, at) : firstToken;
        return command.equalsIgnoreCase(name);
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("command name must not be blank");
        }
        String trimmed = name.strip();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("command name must not be blank");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
