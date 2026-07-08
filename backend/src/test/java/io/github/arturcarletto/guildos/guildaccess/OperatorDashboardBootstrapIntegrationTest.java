package io.github.arturcarletto.guildos.guildaccess;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduces the operator dashboard bootstrap sequence a freshly authenticated browser performs
 * after Discord OAuth login: {@code GET /api/v1/me} then {@code GET /api/v1/guilds}. It also asserts
 * that the Discord-backed eligible-guilds endpoint degrades to a controlled status rather than a raw
 * 500 when Discord is unavailable or fails unexpectedly.
 */
@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=true",
        "guildos.identity.discord-oauth.client-id=fake-client-id",
        "guildos.identity.discord-oauth.client-secret=fake-client-secret-value"
})
@Import(TestcontainersConfiguration.class)
class OperatorDashboardBootstrapIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private OperatorGuildAccessRepository repository;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private DiscordGuildClient discordGuildClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.flush();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void dashboardBootstrapLoadsOperatorAndEmptyGuildsWithoutError() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("dash");

        mockMvc.perform(get("/api/v1/me").accept(MediaType.APPLICATION_JSON)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").isNotEmpty())
                .andExpect(jsonPath("$.username").value("user-dash"));

        // A fresh operator with no onboarded guilds must get a clean empty list, never a 5xx.
        mockMvc.perform(get("/api/v1/guilds").accept(MediaType.APPLICATION_JSON)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        // Refreshing the dashboard stays stable and authenticated.
        mockMvc.perform(get("/api/v1/guilds").accept(MediaType.APPLICATION_JSON)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void eligibleGuildsReturnsServiceUnavailableWhenDiscordIsUnavailable() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("unavailable");
        seedAuthorizedClient(operator);
        when(discordGuildClient.fetchOperatorGuilds(anyString()))
                .thenThrow(new DiscordUnavailableException("Discord is temporarily unavailable"));

        mockMvc.perform(get("/api/v1/onboarding/guilds").accept(MediaType.APPLICATION_JSON)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"error\":\"service_unavailable\"}"));
    }

    @Test
    void eligibleGuildsReturnsControlledErrorWhenDiscordFailsUnexpectedly() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("unexpected");
        seedAuthorizedClient(operator);
        when(discordGuildClient.fetchOperatorGuilds(anyString()))
                .thenThrow(new IllegalStateException("unexpected boom"));

        mockMvc.perform(get("/api/v1/onboarding/guilds").accept(MediaType.APPLICATION_JSON)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"server_error\"}"));
    }

    private AuthenticatedOperator operatorPrincipal(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(
                new OperatorLoginCommand("op-" + suffix, "user-" + suffix, "User " + suffix, "avatar")));
    }

    private void seedAuthorizedClient(AuthenticatedOperator operator) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("discord");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "discord-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        OAuth2AuthorizedClient authorizedClient =
                new OAuth2AuthorizedClient(registration, operator.getName(), accessToken);
        Authentication authentication =
                new OAuth2AuthenticationToken(operator, operator.getAuthorities(), "discord");
        authorizedClientService.saveAuthorizedClient(authorizedClient, authentication);
    }
}
