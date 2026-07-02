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

    private final boolean enabled;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public DiscordOAuthProperties(boolean enabled, String clientId, String clientSecret, String redirectUri) {
        this.enabled = enabled;
        this.clientId = normalize(clientId);
        this.clientSecret = normalize(clientSecret);
        this.redirectUri = StringUtils.hasText(redirectUri) ? redirectUri.trim() : DEFAULT_REDIRECT_URI;
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
        return "DiscordOAuthProperties{enabled=%s, clientIdConfigured=%s, clientSecretConfigured=%s, redirectUri='%s'}"
                .formatted(
                        enabled,
                        StringUtils.hasText(clientId),
                        StringUtils.hasText(clientSecret),
                        redirectUri);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
