package io.github.arturcarletto.guildos.guildactivity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GuildActivityConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GuildActivityConfiguration.class);

    @Test
    void invalidRetryDelayRelationshipFailsStartup() {
        contextRunner
                .withPropertyValues(
                        "guildos.activity.processing.initial-retry-delay-ms=5000",
                        "guildos.activity.processing.max-retry-delay-ms=1000")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "guildos.activity.processing.max-retry-delay-ms must be greater than or equal to initial-retry-delay-ms");
                });
    }

    @Test
    void disabledSchedulerDoesNotInvokeProcessor() {
        GuildActivityProcessor processor = mock(GuildActivityProcessor.class);
        GuildActivityProcessorProperties properties = new GuildActivityProcessorProperties();
        properties.setEnabled(false);

        new GuildActivityScheduler(processor, properties).processAvailableEvents();

        verifyNoInteractions(processor);
    }

    @Test
    void enabledSchedulerInvokesProcessor() {
        GuildActivityProcessor processor = mock(GuildActivityProcessor.class);
        GuildActivityProcessorProperties properties = new GuildActivityProcessorProperties();
        properties.setEnabled(true);

        new GuildActivityScheduler(processor, properties).processAvailableEvents();

        verify(processor).processAvailableBatch();
    }
}
