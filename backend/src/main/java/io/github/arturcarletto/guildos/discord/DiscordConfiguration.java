package io.github.arturcarletto.guildos.discord;

import java.util.EnumSet;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guildstatus.GuildStatusService;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordProperties.class)
class DiscordConfiguration {

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordCommandCatalog discordCommandCatalog() {
        return new DiscordCommandCatalog();
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGuildCommandRegistrar discordGuildCommandRegistrar(DiscordCommandCatalog commandCatalog) {
        return new DiscordGuildCommandRegistrar(commandCatalog);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGuildEventListener discordGuildEventListener(
            GuildConnectionService guildConnectionService,
            DiscordGuildCommandRegistrar commandRegistrar) {
        return new DiscordGuildEventListener(guildConnectionService, commandRegistrar);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordSlashCommandListener discordSlashCommandListener(GuildStatusService statusService) {
        return new DiscordSlashCommandListener(statusService);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordJdaFactory discordJdaFactory(
            DiscordGuildEventListener guildEventListener,
            DiscordSlashCommandListener slashCommandListener) {
        return token -> JDABuilder.createLight(token, gatewayIntents())
                .setAutoReconnect(true)
                .addEventListeners(guildEventListener, slashCommandListener)
                .build();
    }

    static EnumSet<GatewayIntent> gatewayIntents() {
        return EnumSet.noneOf(GatewayIntent.class);
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
