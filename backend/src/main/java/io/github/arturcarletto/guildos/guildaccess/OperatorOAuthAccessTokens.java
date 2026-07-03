package io.github.arturcarletto.guildos.guildaccess;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves the current operator's Discord OAuth access token from the authorized-client store.
 *
 * <p>Loading through {@link OAuth2AuthorizedClientRepository} (rather than the
 * {@code @RegisteredOAuth2AuthorizedClient} argument resolver) returns {@code null} when no client is
 * present instead of triggering an authorization redirect, so a missing/expired authorization yields
 * a controlled JSON 401. The provider is optional so the application context still loads when Discord
 * OAuth is disabled.
 */
@Component
class OperatorOAuthAccessTokens {

    private static final String REGISTRATION_ID = "discord";

    private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepository;

    OperatorOAuthAccessTokens(ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepository) {
        this.authorizedClientRepository = authorizedClientRepository;
    }

    String currentAccessToken(Authentication authentication, HttpServletRequest request) {
        OAuth2AuthorizedClientRepository repository = authorizedClientRepository.getIfAvailable();
        if (repository == null) {
            throw new DiscordReauthenticationRequiredException("Discord authorization is required");
        }
        OAuth2AuthorizedClient client =
                repository.loadAuthorizedClient(REGISTRATION_ID, authentication, request);
        if (client == null || client.getAccessToken() == null) {
            throw new DiscordReauthenticationRequiredException("Discord authorization is required");
        }
        return client.getAccessToken().getTokenValue();
    }
}
