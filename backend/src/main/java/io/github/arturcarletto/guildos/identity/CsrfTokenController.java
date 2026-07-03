package io.github.arturcarletto.guildos.identity;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivers the current CSRF token to an authenticated client so it can send state-changing requests.
 *
 * <p>The endpoint stays under the authenticated {@code /api/**} policy. Accessing the injected
 * {@link CsrfToken} materializes and persists the token in the server-side session; the client then
 * echoes the returned value in the token header on POST, DELETE, and {@code POST /logout}.
 */
@RestController
@RequestMapping("/api/v1/csrf")
class CsrfTokenController {

    @GetMapping
    CsrfTokenResponse csrfToken(CsrfToken token) {
        return CsrfTokenResponse.from(token);
    }
}
