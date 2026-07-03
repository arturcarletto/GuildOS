package io.github.arturcarletto.guildos.guildaccess;

/**
 * Result of an onboarding operation on an operator-to-guild authorization.
 *
 * <p>{@link #CREATED} maps to HTTP 201; {@link #REACTIVATED}, {@link #ROLE_UPDATED}, and
 * {@link #UNCHANGED} map to HTTP 200. {@link #UNCHANGED} performs no write, so the row's
 * {@code updatedAt} and optimistic-lock {@code version} are left untouched.
 */
enum OnboardingOutcome {
    CREATED,
    REACTIVATED,
    ROLE_UPDATED,
    UNCHANGED
}
