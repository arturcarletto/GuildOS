package io.github.arturcarletto.guildos.guildaccess;

/**
 * Consistent JSON error body ({@code {"error":"..."}}) shared by API exception handlers. It never
 * carries internal exception detail, tokens, secrets, or external response bodies.
 */
public record ApiErrorResponse(String error) {
}
