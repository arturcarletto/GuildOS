package io.github.arturcarletto.guildos.guildactivity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("guildos.activity.processing")
class GuildActivityProcessorProperties {

    private boolean enabled = true;

    @Min(100)
    @Max(300_000)
    private long fixedDelayMs = 10_000;

    @Min(1)
    @Max(500)
    private int batchSize = 100;

    @Min(1)
    @Max(50)
    private int maxAttempts = 5;

    @Min(100)
    @Max(3_600_000)
    private long initialRetryDelayMs = 1_000;

    @Min(100)
    @Max(86_400_000)
    private long maxRetryDelayMs = 60_000;

    @Min(1_000)
    @Max(86_400_000)
    private long staleLockTimeoutMs = 300_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialRetryDelayMs() {
        return initialRetryDelayMs;
    }

    public void setInitialRetryDelayMs(long initialRetryDelayMs) {
        this.initialRetryDelayMs = initialRetryDelayMs;
    }

    public long getMaxRetryDelayMs() {
        return maxRetryDelayMs;
    }

    public void setMaxRetryDelayMs(long maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs;
    }

    public long getStaleLockTimeoutMs() {
        return staleLockTimeoutMs;
    }

    public void setStaleLockTimeoutMs(long staleLockTimeoutMs) {
        this.staleLockTimeoutMs = staleLockTimeoutMs;
    }
}
