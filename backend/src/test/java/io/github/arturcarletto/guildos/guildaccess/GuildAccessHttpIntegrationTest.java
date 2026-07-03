package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=true",
        "guildos.identity.discord-oauth.client-id=fake-client-id",
        "guildos.identity.discord-oauth.client-secret=fake-client-secret-value"
})
@Import({TestcontainersConfiguration.class, MutableClockTestConfiguration.class})
class GuildAccessHttpIntegrationTest {

    private static final String GUILD_ID = "100000000000000123";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private OperatorGuildAccessRepository repository;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    private MutableTestClock clock;

    @MockitoBean
    private DiscordGuildClient discordGuildClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.setInstant(MutableClockTestConfiguration.INITIAL_INSTANT);
        repository.deleteAll();
        repository.flush();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void onboardingListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/guilds"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void authorizedGuildsListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/guilds"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void listsEligibleConnectedGuildsWithoutLeakingSensitiveFields() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("list");
        connectGuild(GUILD_ID);
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operator);

        mockMvc.perform(get("/api/v1/onboarding/guilds").with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].guildId").value(GUILD_ID))
                .andExpect(jsonPath("$[0].discordRole").value("OWNER"))
                .andExpect(jsonPath("$[0].onboardingStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$[0].permissions").doesNotExist());
    }

    @Test
    void readOnlyListDoesNotRequireCsrf() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("get-nocsrf");

        mockMvc.perform(get("/api/v1/guilds").with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk());
    }

    @Test
    void onboardsEligibleConnectedGuild() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("onboard");
        connectGuild(GUILD_ID);
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void repeatedOnboardingReturnsOk() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("repeat");
        connectGuild(GUILD_ID);
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void onboardingRejectsInsufficientDiscordPermissions() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("ineligible");
        connectGuild(GUILD_ID);
        when(discordGuildClient.fetchOperatorGuilds(anyString())).thenReturn(List.of(
                new OperatorDiscordGuild(GUILD_ID, "Guild", "icon", false, BigInteger.ZERO)));
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));
    }

    @Test
    void onboardingRejectsGuildWhereBotIsDisconnected() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("disconnected");
        connectGuild(GUILD_ID);
        guildConnectionService.disconnect(new DisconnectGuildCommand(GUILD_ID));
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    @Test
    void invalidSnowflakeIsRejected() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("bad-id");
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", "not-a-snowflake")
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void revocationAffectsOnlyTheCurrentOperatorAndIsIdempotent() throws Exception {
        AuthenticatedOperator operatorOne = operatorPrincipal("owner-a");
        AuthenticatedOperator operatorTwo = operatorPrincipal("owner-b");
        connectGuild(GUILD_ID);
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operatorOne);
        seedAuthorizedClient(operatorTwo);

        onboard(operatorOne);
        onboard(operatorTwo);

        mockMvc.perform(delete("/api/v1/guilds/{id}/access", GUILD_ID)
                        .with(oauth2Login().oauth2User(operatorOne)).with(csrf()))
                .andExpect(status().isNoContent());
        // Repeated revocation stays idempotent.
        mockMvc.perform(delete("/api/v1/guilds/{id}/access", GUILD_ID)
                        .with(oauth2Login().oauth2User(operatorOne)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/guilds").with(oauth2Login().oauth2User(operatorOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/api/v1/guilds").with(oauth2Login().oauth2User(operatorTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].guildId").value(GUILD_ID))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void stateChangingRequestWithoutCsrfIsForbidden() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("no-csrf");
        connectGuild(GUILD_ID);
        stubOwnerGuild(GUILD_ID);
        seedAuthorizedClient(operator);

        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));
    }

    @Test
    void missingDiscordAuthorizedClientReturnsControlledJsonUnauthorized() throws Exception {
        AuthenticatedOperator operator = operatorPrincipal("no-client");

        mockMvc.perform(get("/api/v1/onboarding/guilds").with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    private void onboard(AuthenticatedOperator operator) throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/guilds/{id}", GUILD_ID)
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isCreated());
    }

    private AuthenticatedOperator operatorPrincipal(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(
                new OperatorLoginCommand("op-" + suffix, "user-" + suffix, "User " + suffix, "avatar")));
    }

    private void connectGuild(String discordGuildId) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + discordGuildId));
    }

    private void stubOwnerGuild(String discordGuildId) {
        when(discordGuildClient.fetchOperatorGuilds(anyString())).thenReturn(List.of(
                new OperatorDiscordGuild(discordGuildId, "Guild", "icon", true, BigInteger.ZERO)));
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
