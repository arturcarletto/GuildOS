package io.github.arturcarletto.guildos.guildactivity;

import java.time.Duration;
import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
class GuildActivityMetrics {

    private static final String UNKNOWN_EVENT_TYPE = "unknown";

    private final MeterRegistry registry;

    GuildActivityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordIngestion(GuildActivityEventType eventType, String outcome) {
        Counter.builder("guildos.activity.ingestion")
                .tag("event_type", eventTypeTag(eventType))
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    void recordProcessing(GuildActivityEventType eventType, String outcome, Duration duration) {
        Counter.builder("guildos.activity.processing")
                .tag("event_type", eventTypeTag(eventType))
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("guildos.activity.processing.duration")
                .tag("event_type", eventTypeTag(eventType))
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    private static String eventTypeTag(GuildActivityEventType eventType) {
        return eventType == null ? UNKNOWN_EVENT_TYPE : eventType.name().toLowerCase(Locale.ROOT);
    }
}
