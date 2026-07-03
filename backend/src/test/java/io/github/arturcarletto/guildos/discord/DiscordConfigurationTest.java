package io.github.arturcarletto.guildos.discord;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guildstatus.GuildStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiscordConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DiscordConfiguration.class)
            .withBean(GuildConnectionService.class, () -> mock(GuildConnectionService.class))
            .withBean(GuildStatusService.class, () -> mock(GuildStatusService.class));

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
                    assertThat(context).doesNotHaveBean(DiscordGuildCommandRegistrar.class);
                    assertThat(context).doesNotHaveBean(DiscordCommandCatalog.class);
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
    void gatewayUsesNoAdditionalIntents() {
        assertThat(DiscordConfiguration.gatewayIntents()).isEmpty();
    }
}
