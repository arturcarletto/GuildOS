package io.github.arturcarletto.guildos.identity;

import org.springframework.security.web.csrf.CsrfToken;

/**
 * Safe view of the current CSRF token for an authenticated client. It exposes only the values a
 * client needs to send the token back on state-changing requests, and nothing else.
 */
public record CsrfTokenResponse(String headerName, String parameterName, String token) {

    static CsrfTokenResponse from(CsrfToken token) {
        return new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
}
