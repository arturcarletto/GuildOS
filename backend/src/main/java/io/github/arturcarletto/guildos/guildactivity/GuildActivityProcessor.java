package io.github.arturcarletto.guildos.guildactivity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class GuildActivityProcessor {

    private final GuildActivityProcessorStore store;
    private final GuildActivityProcessorProperties properties;
    private final GuildActivityMetrics metrics;
    private final Clock clock;

    GuildActivityProcessor(
            GuildActivityProcessorStore store,
            GuildActivityProcessorProperties properties,
            GuildActivityMetrics metrics,
            Clock clock) {
        this.store = store;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public int processAvailableBatch() {
        Instant claimTime = clock.instant();
        List<GuildActivityEventSnapshot> events = store.claimBatch(
                properties.getBatchSize(),
                claimTime,
                Duration.ofMillis(properties.getStaleLockTimeoutMs()));
        for (GuildActivityEventSnapshot event : events) {
            process(event);
        }
        return events.size();
    }

    private void process(GuildActivityEventSnapshot event) {
        Instant started = clock.instant();
        try {
            boolean processed = store.applyProjectionAndMarkProcessed(event, clock.instant());
            metrics.recordProcessing(
                    event.eventType(),
                    processed ? "processed" : "stale_reclaimed",
                    Duration.between(started, clock.instant()));
        } catch (RuntimeException failure) {
            boolean dead = store.recordFailure(
                    event,
                    failureCategory(failure),
                    clock.instant(),
                    properties.getMaxAttempts(),
                    Duration.ofMillis(properties.getInitialRetryDelayMs()),
                    Duration.ofMillis(properties.getMaxRetryDelayMs()));
            metrics.recordProcessing(
                    event.eventType(),
                    dead ? "dead" : "retry_scheduled",
                    Duration.between(started, clock.instant()));
        }
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        if (category == null || category.isBlank()) {
            return "UnknownFailure";
        }
        String bounded = category.replaceAll("[^A-Za-z0-9_.$-]", "_");
        return bounded.length() <= 120 ? bounded : bounded.substring(0, 120);
    }
}
