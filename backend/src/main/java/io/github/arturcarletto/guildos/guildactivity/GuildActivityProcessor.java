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
            if (event.staleReclaimed()) {
                metrics.recordProcessing(event.eventType(), "stale_reclaimed", Duration.ZERO);
            }
            process(event);
        }
        return events.size();
    }

    void process(GuildActivityEventSnapshot event) {
        Instant started = clock.instant();
        try {
            GuildActivityProcessingResult result =
                    store.applyProjectionAndMarkProcessed(event, clock.instant());
            metrics.recordProcessing(
                    event.eventType(),
                    result == GuildActivityProcessingResult.PROCESSED ? "processed" : "claim_lost",
                    Duration.between(started, clock.instant()));
        } catch (RuntimeException failure) {
            GuildActivityFailureResult result = store.recordFailure(
                    event,
                    failureCategory(failure),
                    clock.instant(),
                    properties.getMaxAttempts(),
                    Duration.ofMillis(properties.getInitialRetryDelayMs()),
                    Duration.ofMillis(properties.getMaxRetryDelayMs()));
            metrics.recordProcessing(
                    event.eventType(),
                    failureOutcome(result),
                    Duration.between(started, clock.instant()));
        }
    }

    private static String failureOutcome(GuildActivityFailureResult result) {
        return switch (result) {
            case RETRY_SCHEDULED -> "retry_scheduled";
            case DEAD -> "dead";
            case CLAIM_LOST -> "claim_lost";
        };
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
