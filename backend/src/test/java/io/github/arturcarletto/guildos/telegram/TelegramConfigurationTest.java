package io.github.arturcarletto.guildos.telegram;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.arturcarletto.guildos.platform.PlatformMessageSender;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramConfigurationTest {

    /** Stub client so the enabled-context test never performs a real Telegram HTTP call. */
    private static final TelegramApiClient STUB_CLIENT = new TelegramApiClient() {
        @Override
        public List<TelegramUpdate> getUpdates(long offset) {
            return List.of();
        }

        @Override
        public void sendMessage(long chatId, String text) {
            // no-op
        }
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelegramConfiguration.class)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void disabledAdapterStartsWithoutATokenAndCreatesNoRuntimeBeans() {
        contextRunner
                .withPropertyValues("guildos.telegram.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TelegramProperties.class);
                    assertThat(context).doesNotHaveBean(TelegramApiClient.class);
                    assertThat(context).doesNotHaveBean(PlatformMessageSender.class);
                    assertThat(context).doesNotHaveBean(TelegramCommandHandler.class);
                    assertThat(context).doesNotHaveBean(TelegramUpdatePoller.class);
                });
    }

    @Test
    void enabledAdapterRejectsABlankToken() {
        contextRunner
                .withPropertyValues(
                        "guildos.telegram.enabled=true",
                        "guildos.telegram.bot-token=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "guildos.telegram.bot-token must be configured when guildos.telegram.enabled=true");
                });
    }

    @Test
    void enabledAdapterWiresThePollerWhenATokenIsPresent() {
        contextRunner
                .withAllowBeanDefinitionOverriding(true)
                .withBean("telegramApiClient", TelegramApiClient.class, () -> STUB_CLIENT)
                .withPropertyValues(
                        "guildos.telegram.enabled=true",
                        "guildos.telegram.bot-token=fake-telegram-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PlatformMessageSender.class);
                    assertThat(context).hasSingleBean(TelegramCommandHandler.class);
                    assertThat(context).hasSingleBean(TelegramUpdatePoller.class);
                });
    }

    @Test
    void propertiesDoNotExposeTheTokenInTheirStringRepresentation() {
        TelegramProperties properties =
                new TelegramProperties(true, "secret-telegram-token", Duration.ofSeconds(2));

        assertThat(properties.toString())
                .contains("enabled=true", "botTokenConfigured=true")
                .doesNotContain("secret-telegram-token");
    }

    @Test
    void pollIntervalDefaultsWhenNotConfigured() {
        TelegramProperties properties = new TelegramProperties(false, null, null);

        assertThat(properties.getPollInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getBotToken()).isEmpty();
    }
}
