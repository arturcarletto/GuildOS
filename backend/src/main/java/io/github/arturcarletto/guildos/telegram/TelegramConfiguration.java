package io.github.arturcarletto.guildos.telegram;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import io.github.arturcarletto.guildos.platform.PlatformMessageSender;

/**
 * Wires the experimental Telegram adapter. {@link TelegramProperties} is always bound so a
 * misconfiguration fails fast, but every runtime bean is created only when
 * {@code guildos.telegram.enabled=true}. When Telegram is disabled no client, sender, handler, or
 * poller exists, so no HTTP call is made and no polling thread starts.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelegramProperties.class)
class TelegramConfiguration {

    @Bean
    @ConditionalOnProperty(name = "guildos.telegram.enabled", havingValue = "true")
    TelegramApiClient telegramApiClient(
            ObjectProvider<RestClient.Builder> restClientBuilder,
            TelegramProperties properties) {
        return new RestClientTelegramApiClient(
                restClientBuilder.getIfAvailable(RestClient::builder), properties);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.telegram.enabled", havingValue = "true")
    PlatformMessageSender telegramMessageSender(TelegramApiClient telegramApiClient) {
        return new TelegramMessageSender(telegramApiClient);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.telegram.enabled", havingValue = "true")
    TelegramCommandHandler telegramCommandHandler(
            PlatformMessageSender telegramMessageSender, Clock clock) {
        return new TelegramCommandHandler(telegramMessageSender, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.telegram.enabled", havingValue = "true")
    TelegramUpdatePoller telegramUpdatePoller(
            TelegramProperties properties,
            TelegramApiClient telegramApiClient,
            TelegramCommandHandler telegramCommandHandler) {
        return new TelegramUpdatePoller(properties, telegramApiClient, telegramCommandHandler);
    }
}
