package io.github.arturcarletto.guildos;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides a controllable {@link MutableTestClock} as the primary {@link java.time.Clock}
 * so a test can advance time between operations. It replaces the production clock without
 * introducing another production clock bean.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockTestConfiguration {

    public static final Instant INITIAL_INSTANT = Instant.parse("2026-01-02T03:04:05Z");

    @Bean
    @Primary
    MutableTestClock mutableTestClock() {
        return new MutableTestClock(INITIAL_INSTANT);
    }
}