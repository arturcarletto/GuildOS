package io.github.arturcarletto.guildos.guildaccess;

/**
 * Consistent JSON error body ({@code {"error":"..."}}) shared by the guildaccess handlers. It never
 * carries internal exception detail, tokens, secrets, or Discord response bodies.
 */
public record ApiErrorResponse(String error) {
}
