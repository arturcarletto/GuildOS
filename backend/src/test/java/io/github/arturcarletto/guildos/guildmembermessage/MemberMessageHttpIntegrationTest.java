package io.github.arturcarletto.guildos.guildmembermessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class MemberMessageHttpIntegrationTest {

    private static final String GUILD_ID = "200000000000000456";
    private static final String CHANNEL_ID = "300000000000000789";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GuildMemberMessageConfigurationRepository repository;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private MutableTestClock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.setInstant(MutableClockTestConfiguration.INITIAL_INSTANT);
        repository.deleteAll();
        repository.flush();
        accessFixture.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedRequestsReturnJsonUnauthorized() throws Exception {
        mockMvc.perform(get(url(GUILD_ID, "welcome")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
        mockMvc.perform(put(url(GUILD_ID, "welcome"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(welcomeBody("Hi", "Welcome {member}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unconfiguredGetReturnsSafeUnconfiguredResponse() throws Exception {
        AuthenticatedOperator operator = authorize("get-empty");

        mockMvc.perform(get(url(GUILD_ID, "welcome")).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WELCOME"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.channelId").doesNotExist())
                .andExpect(jsonPath("$.registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        mockMvc.perform(get(url(GUILD_ID, "goodbye")).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("GOODBYE"))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.mentionMember").doesNotExist());
    }

    @Test
    void authorizedOperatorCanSaveWelcomeConfiguration() throws Exception {
        AuthenticatedOperator operator = authorize("save-welcome");

        saveWelcome(operator, welcomeBody("Welcome {member}!", "Hey {member}, welcome to {server}!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WELCOME"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.channelId").value(CHANNEL_ID))
                .andExpect(jsonPath("$.title").value("Welcome {member}!"))
                .andExpect(jsonPath("$.message").value("Hey {member}, welcome to {server}!"))
                .andExpect(jsonPath("$.color").value("#57F287"))
                .andExpect(jsonPath("$.mentionMember").value(true))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void authorizedOperatorCanSaveGoodbyeConfiguration() throws Exception {
        AuthenticatedOperator operator = authorize("save-goodbye");

        mockMvc.perform(put(url(GUILD_ID, "goodbye"))
                        .with(oauth2Login().oauth2User(operator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                "channelId":"%s","message":"{member} has left {server}","color":"#ED4245"
                                """.formatted(CHANNEL_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("GOODBYE"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.message").value("{member} has left {server}"))
                .andExpect(jsonPath("$.mentionMember").doesNotExist());
    }

    @Test
    void toggleRequiresExistingConfigurationAndFlipsEnabledState() throws Exception {
        AuthenticatedOperator operator = authorize("toggle");

        // Toggling before any configuration exists must not create one.
        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"not_configured\"}"));

        saveWelcome(operator, welcomeBody("Welcome", "Hi {member}")).andExpect(status().isOk());

        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.channelId").value(CHANNEL_ID));

        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(operator)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void previewRendersSafeSampleValuesWithoutSendingToDiscord() throws Exception {
        AuthenticatedOperator operator = authorize("preview");

        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/preview")
                        .with(oauth2Login().oauth2User(operator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                "channelId":"%s","title":"Hi {member}",\
                                "message":"Welcome {member} to {server}! You are member #{memberCount}.",\
                                "color":"#57F287","mentionMember":true
                                """.formatted(CHANNEL_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("WELCOME"))
                .andExpect(jsonPath("$.title").value("Hi Sample Member"))
                .andExpect(jsonPath("$.description")
                        .value("Welcome Sample Member to Guild " + GUILD_ID + "! You are member #1234."))
                .andExpect(jsonPath("$.color").value("#57F287"))
                .andExpect(jsonPath("$.memberCount").value(1234))
                .andExpect(jsonPath("$.mentionMember").value(true));

        // Preview never persists a configuration.
        mockMvc.perform(get(url(GUILD_ID, "welcome")).with(oauth2Login().oauth2User(operator)))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void goodbyeRejectsMentionPlaceholderAndWelcomeOnlyMisuse() throws Exception {
        AuthenticatedOperator operator = authorize("goodbye-misuse");

        assertBadRequest(operator, "goodbye", body("""
                "channelId":"%s","message":"Bye {mention}"
                """.formatted(CHANNEL_ID)));
        assertBadRequest(operator, "goodbye", body("""
                "channelId":"%s","message":"Bye {member}","mentionMember":true
                """.formatted(CHANNEL_ID)));
        assertBadRequest(operator, "goodbye", body("""
                "channelId":"%s","message":"Bye","buttonLabel":"Docs","buttonUrl":"https://example.com"
                """.formatted(CHANNEL_ID)));
    }

    @Test
    void unsafeMentionsAndInvalidAppearanceValuesAreRejected() throws Exception {
        AuthenticatedOperator operator = authorize("validation");

        assertBadRequest(operator, "welcome", welcomeBody("Hi", "Hello @everyone"));
        assertBadRequest(operator, "welcome", welcomeBody("Hi", "Hello <@123456789012345678>"));
        assertBadRequest(operator, "welcome", welcomeBody("Hi {unknownPlaceholder}", "Hello {member}"));
        assertBadRequest(operator, "welcome", body("""
                "channelId":"%s","message":"Hi","color":"not-a-color"
                """.formatted(CHANNEL_ID)));
        assertBadRequest(operator, "welcome", body("""
                "channelId":"%s","message":"Hi","imageUrl":"http://insecure.example.com/x.png"
                """.formatted(CHANNEL_ID)));
        assertBadRequest(operator, "welcome", body("""
                "channelId":"%s","message":"Hi","buttonLabel":"Docs"
                """.formatted(CHANNEL_ID)));
        assertBadRequest(operator, "welcome", body("""
                "channelId":"not-a-snowflake","message":"Hi"
                """));
    }

    @Test
    void unauthorizedOperatorCannotReadUpdateToggleOrPreview() throws Exception {
        authorize("authorized-owner");
        AuthenticatedOperator stranger = newOperator("stranger");

        mockMvc.perform(get(url(GUILD_ID, "welcome")).with(oauth2Login().oauth2User(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
        mockMvc.perform(put(url(GUILD_ID, "welcome"))
                        .with(oauth2Login().oauth2User(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(welcomeBody("Hi", "Hello {member}")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(stranger)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/preview")
                        .with(oauth2Login().oauth2User(stranger)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(welcomeBody("Hi", "Hello {member}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void stateChangingRequestsWithoutCsrfAreForbidden() throws Exception {
        AuthenticatedOperator operator = authorize("csrf");

        mockMvc.perform(put(url(GUILD_ID, "welcome"))
                        .with(oauth2Login().oauth2User(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(welcomeBody("Hi", "Hello {member}")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));
        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url(GUILD_ID, "welcome") + "/preview")
                        .with(oauth2Login().oauth2User(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(welcomeBody("Hi", "Hello {member}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidGuildIdReturnsBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("bad-guild");

        mockMvc.perform(get(url("not-a-snowflake", "welcome")).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    private ResultActions saveWelcome(AuthenticatedOperator operator, String requestBody) throws Exception {
        return mockMvc.perform(put(url(GUILD_ID, "welcome"))
                .with(oauth2Login().oauth2User(operator)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }

    private void assertBadRequest(AuthenticatedOperator operator, String kind, String requestBody)
            throws Exception {
        mockMvc.perform(put(url(GUILD_ID, kind))
                        .with(oauth2Login().oauth2User(operator)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    private AuthenticatedOperator authorize(String suffix) {
        AuthenticatedOperator operator = newOperator(suffix);
        RegisteredGuildView guild = connectGuild();
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "mm-op-" + suffix, "mm-" + suffix, "Member Message " + suffix, "avatar")));
    }

    private RegisteredGuildView connectGuild() {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + GUILD_ID));
        return guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
    }

    private static String url(String discordGuildId, String kind) {
        return "/api/v1/guilds/" + discordGuildId + "/member-messages/" + kind;
    }

    private static String welcomeBody(String title, String message) {
        return body("""
                "channelId":"%s","title":"%s","message":"%s"
                """.formatted(CHANNEL_ID, title, message));
    }

    private static String body(String fields) {
        return "{" + fields.strip() + "}";
    }
}
