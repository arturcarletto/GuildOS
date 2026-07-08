package io.github.arturcarletto.guildos.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the configurable OAuth success-redirect resolves correctly from the real
 * configuration files. Uses {@link ConfigDataApplicationContextInitializer} so the actual
 * {@code application.yml} / {@code application-local.yml} are loaded, but without starting the
 * database or the rest of the application context.
 */
class DiscordOAuthSuccessRedirectProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultProfileRedirectsToTheCurrentOperatorEndpoint() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DiscordOAuthProperties.class).getSuccessRedirectUri())
                    .isEqualTo("/api/v1/me");
        });
    }

    @Test
    void localProfileRedirectsToTheOperatorDashboard() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(DiscordOAuthProperties.class).getSuccessRedirectUri())
                            .isEqualTo("http://localhost:5173/dashboard");
                });
    }

    @EnableConfigurationProperties(DiscordOAuthProperties.class)
    static class PropertiesConfiguration {
    }
}
