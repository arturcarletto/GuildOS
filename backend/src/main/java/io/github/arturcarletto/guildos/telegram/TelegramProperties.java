package io.github.arturcarletto.guildos.telegram;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the experimental Telegram adapter.
 *
 * <p>Telegram is disabled by default; the application starts without a token. When enabled, a bot
 * token is required and startup fails fast otherwise. The token is treated as a secret: it is
 * {@link JsonIgnore}-d out of any serialized output and is never included in {@link #toString()}, so
 * it cannot leak through actuator, logs, or exception messages.
 */
@Validated
@ConfigurationProperties("guildos.telegram")
public final class TelegramProperties {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(2);

    private final boolean enabled;
    private final String botToken;
    private final Duration pollInterval;

    public TelegramProperties(boolean enabled, String botToken, Duration pollInterval) {
        this.enabled = enabled;
        this.botToken = botToken == null ? "" : botToken.trim();
        this.pollInterval = pollInterval == null ? DEFAULT_POLL_INTERVAL : pollInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnore
    public String getBotToken() {
        return botToken;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    @AssertTrue(message = "guildos.telegram.bot-token must be configured when guildos.telegram.enabled=true")
    @JsonIgnore
    public boolean isBotTokenConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(botToken);
    }

    @AssertTrue(message = "guildos.telegram.poll-interval must be positive")
    @JsonIgnore
    public boolean isPollIntervalPositive() {
        return !pollInterval.isZero() && !pollInterval.isNegative();
    }

    @Override
    public String toString() {
        return "TelegramProperties{enabled=%s, botTokenConfigured=%s, pollInterval=%s}"
                .formatted(enabled, StringUtils.hasText(botToken), pollInterval);
    }
}
