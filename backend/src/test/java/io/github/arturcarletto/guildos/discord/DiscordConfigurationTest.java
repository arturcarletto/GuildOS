package io.github.arturcarletto.guildos.discord;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guildactivity.GuildActivityIngestionService;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildstatus.GuildStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class DiscordConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DiscordConfiguration.class)
            .withBean(GuildConnectionService.class, () -> mock(GuildConnectionService.class))
            .withBean(GuildStatusService.class, () -> mock(GuildStatusService.class))
            .withBean(GuildMemberMessageService.class, () -> mock(GuildMemberMessageService.class))
            .withBean(GuildActivityIngestionService.class, () -> mock(GuildActivityIngestionService.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void disabledIntegrationDoesNotRequireATokenOrCreateGatewayBeans() {
        contextRunner
                .withPropertyValues("guildos.discord.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiscordProperties.class);
                    assertThat(context).doesNotHaveBean(DiscordGateway.class);
                    assertThat(context).doesNotHaveBean(DiscordHealthIndicator.class);
                    assertThat(context).doesNotHaveBean(DiscordGuildEventListener.class);
                    assertThat(context).doesNotHaveBean(DiscordSlashCommandListener.class);
                    assertThat(context).doesNotHaveBean(DiscordMemberMessageCommandListener.class);
                    assertThat(context).doesNotHaveBean(DiscordMemberLifecycleListener.class);
                    assertThat(context).doesNotHaveBean(DiscordGuildActivityListener.class);
                    assertThat(context).doesNotHaveBean(DiscordGuildCommandRegistrar.class);
                    assertThat(context).doesNotHaveBean(DiscordCommandCatalog.class);
                });
    }

    @Test
    void enabledIntegrationRegistersMemberMessageListeners() {
        // Override the JDA factory with a stub so the gateway starts without a real connection.
        JDA jda = mock(JDA.class, RETURNS_DEEP_STUBS);
        contextRunner
                .withAllowBeanDefinitionOverriding(true)
                .withBean("discordJdaFactory", DiscordJdaFactory.class, () -> token -> jda)
                .withPropertyValues(
                        "guildos.discord.enabled=true",
                        "guildos.discord.token=test-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiscordMemberMessageCommandListener.class);
                    assertThat(context).hasSingleBean(DiscordMemberLifecycleListener.class);
                    assertThat(context).hasSingleBean(DiscordGuildActivityListener.class);
                    assertThat(context).hasSingleBean(DiscordMemberMessageEmbedFactory.class);
                    assertThat(context).hasSingleBean(DiscordMemberMessageChannelResolver.class);
                    assertThat(context).hasSingleBean(DiscordMemberMessageDeliveryMetrics.class);
                });
    }

    @Test
    void enabledIntegrationRejectsABlankToken() {
        contextRunner
                .withPropertyValues(
                        "guildos.discord.enabled=true",
                        "guildos.discord.token=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(
                                    "guildos.discord.token must be configured when guildos.discord.enabled=true");
                });
    }

    @Test
    void propertiesDoNotExposeTheTokenInTheirStringRepresentation() {
        DiscordProperties properties = new DiscordProperties(true, "secret-test-token");

        assertThat(properties.toString())
                .contains("enabled=true", "tokenConfigured=true")
                .doesNotContain("secret-test-token");
    }

    @Test
    void gatewayEnablesOnlyMembersAndMessagesWithoutMessageContent() {
        assertThat(DiscordConfiguration.gatewayIntents())
                .containsExactlyInAnyOrder(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
                .doesNotContain(GatewayIntent.MESSAGE_CONTENT);
    }
}
