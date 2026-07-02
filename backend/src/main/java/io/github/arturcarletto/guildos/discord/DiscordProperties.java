package io.github.arturcarletto.guildos.discord;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Validated
@ConfigurationProperties("guildos.discord")
public final class DiscordProperties {

    private final boolean enabled;
    private final String token;

    public DiscordProperties(boolean enabled, String token) {
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnore
    public String getToken() {
        return token;
    }

    @AssertTrue(message = "guildos.discord.token must be configured when guildos.discord.enabled=true")
    @JsonIgnore
    public boolean isTokenConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(token);
    }

    @Override
    public String toString() {
        return "DiscordProperties{enabled=%s, tokenConfigured=%s}"
                .formatted(enabled, StringUtils.hasText(token));
    }
}
