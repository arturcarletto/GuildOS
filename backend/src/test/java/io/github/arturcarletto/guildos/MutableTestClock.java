package io.github.arturcarletto.guildos;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * A test-only {@link Clock} whose instant can be advanced during a test so timestamp
 * behavior across multiple operations can be verified deterministically.
 */
public final class MutableTestClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableTestClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableTestClock(Instant instant, ZoneId zone) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public void setInstant(Instant instant) {
        this.instant = Objects.requireNonNull(instant, "instant must not be null");
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableTestClock(instant, zone);
    }
}