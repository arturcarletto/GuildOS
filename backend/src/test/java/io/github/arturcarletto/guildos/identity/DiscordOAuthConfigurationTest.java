package io.github.arturcarletto.guildos.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiscordOAuthConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DiscordOAuthClientConfiguration.class)
            .withBean(OperatorLoginService.class, () -> mock(OperatorLoginService.class));

    @Test
    void disabledOAuthDoesNotRequireCredentialsOrCreateClientBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DiscordOAuthProperties.class);
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
            assertThat(context).doesNotHaveBean(DiscordOAuth2UserService.class);
        });
    }

    @Test
    void enabledOAuthRejectsAMissingClientId() {
        contextRunner
                .withPropertyValues(
                        "guildos.identity.discord-oauth.enabled=true",
                        "guildos.identity.discord-oauth.client-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "guildos.identity.discord-oauth.client-id must be configured");
                });
    }

    @Test
    void enabledOAuthRejectsAMissingClientSecret() {
        contextRunner
                .withPropertyValues(
                        "guildos.identity.discord-oauth.enabled=true",
                        "guildos.identity.discord-oauth.client-id=test-client")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "guildos.identity.discord-oauth.client-secret must be configured");
                });
    }

    @Test
    void propertiesDoNotExposeTheClientSecret() {
        DiscordOAuthProperties properties = new DiscordOAuthProperties(
                true,
                "test-client",
                "secret-value",
                "",
                "");

        assertThat(properties.toString())
                .contains("enabled=true", "clientSecretConfigured=true")
                .doesNotContain("secret-value");
        assertThat(properties.getRedirectUri()).isEqualTo(DiscordOAuthProperties.DEFAULT_REDIRECT_URI);
    }

    @Test
    void successRedirectUriDefaultsToTheCurrentOperatorEndpoint() {
        DiscordOAuthProperties defaults = new DiscordOAuthProperties(false, "", "", "", "");
        assertThat(defaults.getSuccessRedirectUri()).isEqualTo("/api/v1/me");
        assertThat(DiscordOAuthProperties.DEFAULT_SUCCESS_REDIRECT_URI).isEqualTo("/api/v1/me");

        DiscordOAuthProperties overridden = new DiscordOAuthProperties(
                true,
                "test-client",
                "test-secret",
                "",
                "http://localhost:5173/dashboard");
        assertThat(overridden.getSuccessRedirectUri()).isEqualTo("http://localhost:5173/dashboard");
    }

    @Test
    void enabledOAuthCreatesTheDiscordClientRegistration() {
        contextRunner
                .withPropertyValues(
                        "guildos.identity.discord-oauth.enabled=true",
                        "guildos.identity.discord-oauth.client-id=test-client",
                        "guildos.identity.discord-oauth.client-secret=test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ClientRegistrationRepository repository = context.getBean(ClientRegistrationRepository.class);
                    ClientRegistration registration = repository.findByRegistrationId("discord");

                    assertThat(registration.getAuthorizationGrantType())
                            .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
                    assertThat(registration.getClientAuthenticationMethod())
                            .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                    assertThat(registration.getRedirectUri())
                            .isEqualTo("{baseUrl}/login/oauth2/code/discord");
                    assertThat(registration.getScopes()).containsExactly("identify", "guilds");
                    assertThat(registration.getProviderDetails().getAuthorizationUri())
                            .isEqualTo("https://discord.com/oauth2/authorize");
                    assertThat(registration.getProviderDetails().getTokenUri())
                            .isEqualTo("https://discord.com/api/oauth2/token");
                    assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                            .isEqualTo("https://discord.com/api/v10/users/@me");
                    assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                            .isEqualTo("id");
                });
    }
}
