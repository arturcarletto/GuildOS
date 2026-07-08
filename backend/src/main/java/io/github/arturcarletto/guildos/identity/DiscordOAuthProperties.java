package io.github.arturcarletto.guildos.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("guildos.identity.discord-oauth")
public final class DiscordOAuthProperties {

    static final String DEFAULT_REDIRECT_URI = "{baseUrl}/login/oauth2/code/discord";
    static final String DEFAULT_SUCCESS_REDIRECT_URI = "/api/v1/me";

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String successRedirectUri;

    public DiscordOAuthProperties(
            boolean enabled,
            String clientId,
            String clientSecret,
            String redirectUri,
            String successRedirectUri) {
        this.enabled = enabled;
        this.clientId = normalize(clientId);
        this.clientSecret = normalize(clientSecret);
        this.redirectUri = StringUtils.hasText(redirectUri) ? redirectUri.trim() : DEFAULT_REDIRECT_URI;
        this.successRedirectUri = StringUtils.hasText(successRedirectUri)
                ? successRedirectUri.trim()
                : DEFAULT_SUCCESS_REDIRECT_URI;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getClientId() {
        return clientId;
    }

    @JsonIgnore
    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     * Where Spring Security sends the browser after a successful OAuth2 login. Defaults to
     * {@code /api/v1/me} to preserve the original backend behavior; a deployment that serves the
     * operator dashboard separately can point this at the frontend (for example the local Vite dev
     * server) so operators land back in the dashboard instead of a JSON endpoint.
     */
    public String getSuccessRedirectUri() {
        return successRedirectUri;
    }

    @AssertTrue(message = "guildos.identity.discord-oauth.client-id must be configured when Discord OAuth is enabled")
    @JsonIgnore
    public boolean isClientIdConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(clientId);
    }

    @AssertTrue(message = "guildos.identity.discord-oauth.client-secret must be configured when Discord OAuth is enabled")
    @JsonIgnore
    public boolean isClientSecretConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(clientSecret);
    }

    @Override
    public String toString() {
        return ("DiscordOAuthProperties{enabled=%s, clientIdConfigured=%s, clientSecretConfigured=%s, "
                + "redirectUri='%s', successRedirectUri='%s'}")
                .formatted(
                        enabled,
                        StringUtils.hasText(clientId),
                        StringUtils.hasText(clientSecret),
                        redirectUri,
                        successRedirectUri);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
