package io.github.arturcarletto.guildos.guildmoderation;

import java.util.regex.Pattern;

/**
 * Validated, application-facing member search query for the moderation workflow.
 *
 * <p>This is a live lookup helper, not a persisted directory. The raw query text is never stored or
 * logged; it exists only to help an authorized operator select a moderation target. Only a full
 * Discord snowflake (17-20 digits) is treated as an exact id lookup; any other value is a bounded
 * text search that must be at least {@value #MIN_QUERY_LENGTH} characters. A purely numeric query
 * that is not a full snowflake is rejected rather than run as a numeric prefix search, so a partial
 * id such as {@code "1"} or {@code "12"} cannot bypass the minimum-length rule.
 */
public record MemberSearchQuery(
        String discordGuildId,
        String query,
        boolean exactIdLookup,
        int limit) {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 25;
    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 64;

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{17,20}$");
    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");

    public MemberSearchQuery {
        discordGuildId = requirePresent(discordGuildId, "discordGuildId");
        query = requirePresent(query, "query");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    static MemberSearchQuery of(String discordGuildId, String rawQuery, Integer rawLimit) {
        String normalized = rawQuery == null ? "" : rawQuery.trim();
        if (normalized.isBlank()) {
            throw new InvalidModerationActionException("Search query is required.");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new InvalidModerationActionException(
                    "Search query must be " + MAX_QUERY_LENGTH + " characters or fewer.");
        }
        boolean exactId = SNOWFLAKE.matcher(normalized).matches();
        if (!exactId && DIGITS.matcher(normalized).matches()) {
            throw new InvalidModerationActionException(
                    "Numeric search must be a full Discord user id (17-20 digits).");
        }
        if (!exactId && normalized.length() < MIN_QUERY_LENGTH) {
            throw new InvalidModerationActionException(
                    "Search query must be at least " + MIN_QUERY_LENGTH + " characters.");
        }
        return new MemberSearchQuery(discordGuildId, normalized, exactId, normalizeLimit(rawLimit));
    }

    private static int normalizeLimit(Integer rawLimit) {
        if (rawLimit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, rawLimit));
    }

    private static String requirePresent(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be present");
        }
        return value.trim();
    }
}
