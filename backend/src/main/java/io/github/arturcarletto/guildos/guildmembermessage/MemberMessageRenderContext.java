package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Objects;

/**
 * Already-sanitized values a platform adapter supplies to render a member message.
 *
 * <p>The values are platform-neutral: the Discord adapter Markdown-escapes live member, username
 * and guild names before constructing this context, and generates {@code memberMention} from the
 * affected user's id (never from template text). Escaping can expand a value's length, so the safe
 * bounds below are worst-case escaped lengths. Keeping the contract here — not in the adapter —
 * lets {@link MemberMessageTemplate} validate every template against a maximum context without
 * importing any JDA type.
 */
public record MemberMessageRenderContext(
        String memberDisplayName,
        String username,
        String serverName,
        int memberCount,
        String memberMention) {

    /** Current Discord member display-name / username length limit. */
    static final int MAX_RAW_NAME_LENGTH = 32;

    /** Current Discord guild (server) name length limit. */
    static final int MAX_RAW_SERVER_NAME_LENGTH = 100;

    /**
     * Conservative worst-case Markdown-escape expansion: escaping can prefix every character with a
     * single backslash (for example {@code "_" -> "\_"}), doubling the length.
     */
    static final int MAX_ESCAPE_EXPANSION_FACTOR = 2;

    static final int MAX_MEMBER_DISPLAY_NAME_LENGTH =
            MAX_RAW_NAME_LENGTH * MAX_ESCAPE_EXPANSION_FACTOR;
    static final int MAX_USERNAME_LENGTH = MAX_RAW_NAME_LENGTH * MAX_ESCAPE_EXPANSION_FACTOR;
    static final int MAX_SERVER_NAME_LENGTH =
            MAX_RAW_SERVER_NAME_LENGTH * MAX_ESCAPE_EXPANSION_FACTOR;

    /** A mention is {@code "<@" + up to a 20-digit snowflake + ">"}. */
    static final int MAX_MENTION_LENGTH = 23;

    public MemberMessageRenderContext {
        Objects.requireNonNull(memberDisplayName, "memberDisplayName must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(serverName, "serverName must not be null");
        Objects.requireNonNull(memberMention, "memberMention must not be null");
        if (memberCount < 0) {
            throw new IllegalArgumentException("memberCount must not be negative");
        }
        requireBound("memberDisplayName", memberDisplayName, MAX_MEMBER_DISPLAY_NAME_LENGTH);
        requireBound("username", username, MAX_USERNAME_LENGTH);
        requireBound("serverName", serverName, MAX_SERVER_NAME_LENGTH);
        requireBound("memberMention", memberMention, MAX_MENTION_LENGTH);
    }

    private static void requireBound(String field, String value, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + max + " sanitized characters");
        }
    }

    /**
     * The largest sanitized context a Discord adapter can produce. Every template accepted by
     * {@link MemberMessageTemplate} must render within its budget against this context, which
     * guarantees no valid live context can later overflow an embed field.
     */
    static MemberMessageRenderContext maximum() {
        return new MemberMessageRenderContext(
                "\\_".repeat(MAX_RAW_NAME_LENGTH),
                "\\_".repeat(MAX_RAW_NAME_LENGTH),
                "\\_".repeat(MAX_RAW_SERVER_NAME_LENGTH),
                Integer.MAX_VALUE,
                "<@" + "9".repeat(20) + ">");
    }
}
