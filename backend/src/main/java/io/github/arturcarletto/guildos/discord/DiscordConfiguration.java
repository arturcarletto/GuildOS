package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.util.EnumSet;

import io.micrometer.core.instrument.MeterRegistry;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guildactivity.GuildActivityIngestionService;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSyncService;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildmoderation.GuildModerationDiscordClient;
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
            DiscordGuildCommandRegistrar commandRegistrar,
            DiscordGuildChannelCacheSync channelCacheSync) {
        return new DiscordGuildEventListener(guildConnectionService, commandRegistrar, channelCacheSync);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGuildChannelCacheSync discordGuildChannelCacheSync(
            DiscordGuildChannelSyncService channelSyncService) {
        return new DiscordGuildChannelCacheSync(channelSyncService);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordSlashCommandListener discordSlashCommandListener(GuildStatusService statusService) {
        return new DiscordSlashCommandListener(statusService);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordMemberMessageEmbedFactory discordMemberMessageEmbedFactory() {
        return new DiscordMemberMessageEmbedFactory();
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordMemberMessageChannelResolver discordMemberMessageChannelResolver() {
        return new DiscordMemberMessageChannelResolver();
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordMemberMessageDeliveryMetrics discordMemberMessageDeliveryMetrics(MeterRegistry registry) {
        return new DiscordMemberMessageDeliveryMetrics(registry);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordMemberMessageCommandListener discordMemberMessageCommandListener(
            GuildMemberMessageService memberMessageService,
            DiscordMemberMessageEmbedFactory embedFactory,
            DiscordMemberMessageChannelResolver channelResolver,
            Clock clock) {
        return new DiscordMemberMessageCommandListener(
                memberMessageService, embedFactory, channelResolver, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordMemberLifecycleListener discordMemberLifecycleListener(
            GuildMemberMessageService memberMessageService,
            DiscordMemberMessageEmbedFactory embedFactory,
            DiscordMemberMessageChannelResolver channelResolver,
            DiscordMemberMessageDeliveryMetrics deliveryMetrics,
            Clock clock) {
        return new DiscordMemberLifecycleListener(
                memberMessageService, embedFactory, channelResolver, deliveryMetrics, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGuildActivityListener discordGuildActivityListener(
            GuildActivityIngestionService ingestionService,
            Clock clock) {
        return new DiscordGuildActivityListener(ingestionService, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordJdaFactory discordJdaFactory(
            DiscordGuildEventListener guildEventListener,
            DiscordSlashCommandListener slashCommandListener,
            DiscordMemberMessageCommandListener memberMessageCommandListener,
            DiscordMemberLifecycleListener memberLifecycleListener,
            DiscordGuildActivityListener guildActivityListener) {
        return token -> JDABuilder.createLight(token, gatewayIntents())
                .setAutoReconnect(true)
                .addEventListeners(
                        guildEventListener,
                        slashCommandListener,
                        memberMessageCommandListener,
                        memberLifecycleListener,
                        guildActivityListener)
                .build();
    }

    static EnumSet<GatewayIntent> gatewayIntents() {
        // GUILD_MEMBERS is privileged. GUILD_MESSAGES is standard and does not grant content.
        return EnumSet.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordGateway discordGateway(DiscordProperties properties, DiscordJdaFactory jdaFactory) {
        return new DiscordGateway(properties, jdaFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    GuildModerationDiscordClient guildModerationDiscordClient(DiscordGateway gateway) {
        return new JdaGuildModerationDiscordClient(gateway);
    }

    @Bean("discord")
    @ConditionalOnProperty(name = "guildos.discord.enabled", havingValue = "true")
    DiscordHealthIndicator discordHealthIndicator(DiscordGateway gateway) {
        return new DiscordHealthIndicator(gateway);
    }
}
