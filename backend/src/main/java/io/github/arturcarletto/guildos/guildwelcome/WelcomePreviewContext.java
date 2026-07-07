package io.github.arturcarletto.guildos.guildwelcome;

import java.util.Objects;

/**
 * Already-sanitized values a platform adapter supplies to render a welcome preview.
 *
 * <p>The values are platform-neutral: the Discord adapter Markdown-escapes live member and
 * guild names before constructing this context. Escaping can expand a value's length, so the
 * safe bounds below are expressed in terms of the worst-case escaped length rather than the raw
 * Discord length. Keeping the contract here — and not in the adapter — lets
 * {@link WelcomeTemplate#parse(String)} validate every template against a maximum sanitized
 * context without importing any JDA type.
 */
public record WelcomePreviewContext(
        String memberDisplayName,
        String serverName,
        int memberCount) {

    /** Current Discord member display-name length limit. */
    static final int MAX_RAW_MEMBER_DISPLAY_NAME_LENGTH = 32;

    /** Current Discord guild (server) name length limit. */
    static final int MAX_RAW_SERVER_NAME_LENGTH = 100;

    /**
     * Conservative worst-case Markdown-escape expansion. Escaping can, at worst, prefix every
     * character with a single backslash (for example {@code "_" -> "\_"}), doubling the length.
     */
    static final int MAX_ESCAPE_EXPANSION_FACTOR = 2;

    /** Maximum length of an already-sanitized member display name this context accepts. */
    static final int MAX_MEMBER_DISPLAY_NAME_LENGTH =
            MAX_RAW_MEMBER_DISPLAY_NAME_LENGTH * MAX_ESCAPE_EXPANSION_FACTOR;

    /** Maximum length of an already-sanitized server name this context accepts. */
    static final int MAX_SERVER_NAME_LENGTH =
            MAX_RAW_SERVER_NAME_LENGTH * MAX_ESCAPE_EXPANSION_FACTOR;

    public WelcomePreviewContext {
        Objects.requireNonNull(memberDisplayName, "memberDisplayName must not be null");
        Objects.requireNonNull(serverName, "serverName must not be null");
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount must not be negative");
        }
        if (memberDisplayName.length() > MAX_MEMBER_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "memberDisplayName must not exceed " + MAX_MEMBER_DISPLAY_NAME_LENGTH
                            + " sanitized characters");
        }
        if (serverName.length() > MAX_SERVER_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "serverName must not exceed " + MAX_SERVER_NAME_LENGTH
                            + " sanitized characters");
        }
    }

    /**
     * The largest sanitized context the Discord adapter can produce: worst-case escaped member
     * and server names at their maximum lengths, and the maximum possible member count. Every
     * template accepted by {@link WelcomeTemplate#parse(String)} must render successfully against
     * this context, which guarantees that no valid live context can later overflow the preview.
     */
    static WelcomePreviewContext maximum() {
        return new WelcomePreviewContext(
                "\\_".repeat(MAX_RAW_MEMBER_DISPLAY_NAME_LENGTH),
                "\\_".repeat(MAX_RAW_SERVER_NAME_LENGTH),
                Integer.MAX_VALUE);
    }
}
