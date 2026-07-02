package io.github.arturcarletto.guildos.discord;

import java.util.EnumSet;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordProperties.class)
class DiscordConfiguration {

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordJdaFactory discordJdaFactory() {
        return token -> JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                .setAutoReconnect(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGateway discordGateway(DiscordProperties properties, DiscordJdaFactory jdaFactory) {
        return new DiscordGateway(properties, jdaFactory);
    }

    @Bean("discord")
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordHealthIndicator discordHealthIndicator(DiscordGateway gateway) {
        return new DiscordHealthIndicator(gateway);
    }
}
