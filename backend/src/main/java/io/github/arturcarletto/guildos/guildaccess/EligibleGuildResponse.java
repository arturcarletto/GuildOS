package io.github.arturcarletto.guildos.guildaccess;

/**
 * Safe view of a guild the operator is eligible to onboard. Exposes no permission bitset, OAuth
 * token, session identifier, or internal persistence field.
 *
 * <p>{@code guildId} is the Discord snowflake; {@code discordRole} is the Guild OS role the operator
 * would receive ({@code OWNER}/{@code ADMIN}); {@code onboardingStatus} is one of
 * {@code AVAILABLE}, {@code ONBOARDED}, {@code REVOKED}.
 */
public record EligibleGuildResponse(
        String guildId,
        String name,
        String iconHash,
        String discordRole,
        String onboardingStatus) {
}
