package io.github.arturcarletto.guildos.guildaccess;

/**
 * Internal onboarding result pairing the resource view with the outcome the controller maps to a
 * status code.
 */
record OnboardingResult(OnboardingOutcome outcome, AuthorizedGuildResponse guild) {
}
