package io.github.arturcarletto.guildos.guildmoderation;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class GuildModerationHttpIntegrationTest {

    private static final String GUILD_ID = "200000000000000916";
    private static final String MISSING_GUILD_ID = "200000000000000917";
    private static final String TARGET_USER_ID = "300000000000000916";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @MockitoBean
    private GuildModerationDiscordClient discordClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_audit_events");
        accessFixture.clear();
        reset(discordClient);
        when(discordClient.timeoutMember(any())).thenReturn(ModerationActionResult.success());
        when(discordClient.searchMembers(any())).thenReturn(MemberSearchResult.empty());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedRequestReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(post(url(GUILD_ID))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void missingUnauthorizedAndRevokedAccessReturnSafeNotFound() throws Exception {
        AuthenticatedOperator authorized = authorize("authorized");
        AuthenticatedOperator stranger = newOperator("stranger");

        mockMvc.perform(post(url(MISSING_GUILD_ID))
                        .with(oauth2Login().oauth2User(authorized))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(stranger))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        RegisteredGuildView guild = registeredGuild();
        accessFixture.revoke(authorized.operatorId(), guild.registeredGuildId());
        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(authorized))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    @Test
    void invalidTargetUserIdReturnsBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("bad-target");

        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"targetUserId\":\"not-a-snowflake\",\"durationMinutes\":10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Target user id must be a Discord snowflake."));
    }

    @Test
    void invalidDurationReturnsBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("bad-duration");

        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("\"targetUserId\":\"" + TARGET_USER_ID + "\",\"durationMinutes\":40321")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void successfulTimeoutCallsDiscordAndRecordsSafeAuditEvent() throws Exception {
        AuthenticatedOperator operator = authorize("success");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();

        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.actionType").value("MEMBER_TIMEOUT"))
                .andExpect(jsonPath("$.targetUserId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.durationMinutes").value(10))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.operatorId").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());

        ArgumentCaptor<TimeoutMemberCommand> command = ArgumentCaptor.forClass(TimeoutMemberCommand.class);
        verify(discordClient).timeoutMember(command.capture());
        assertThat(command.getValue().discordGuildId()).isEqualTo(GUILD_ID);
        assertThat(command.getValue().targetUserId()).isEqualTo(TARGET_USER_ID);
        assertThat(command.getValue().duration().toMinutes()).isEqualTo(10);
        assertThat(command.getValue().reason()).contains("Repeated spam after warning.");

        assertThat(auditRowsFor(guild.registeredGuildId()))
                .containsExactly("MEMBER_TIMEOUT_CREATED:OPERATOR:Member timeout was created.:Member timeout");
    }

    @Test
    void discordFailureReturnsControlledResponseAndDoesNotAuditSuccess() throws Exception {
        AuthenticatedOperator operator = authorize("discord-failure");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();
        when(discordClient.timeoutMember(any()))
                .thenThrow(new ModerationDiscordActionException(ModerationFailureCategory.BOT_PERMISSION_MISSING));

        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"bot_permission_missing\"}"))
                .andExpect(jsonPath("$.message").value("The bot cannot timeout members in this guild."));

        assertThat(auditRowsFor(guild.registeredGuildId())).isEmpty();
    }

    @Test
    void unauthenticatedSearchReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "art"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void searchMissingAndUnauthorizedAccessReturnSafeNotFound() throws Exception {
        AuthenticatedOperator authorized = authorize("s-auth");
        AuthenticatedOperator stranger = newOperator("s-strange");

        mockMvc.perform(get(searchUrl(MISSING_GUILD_ID)).param("query", "art")
                        .with(oauth2Login().oauth2User(authorized)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "art")
                        .with(oauth2Login().oauth2User(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    @Test
    void searchBlankShortAndOverlongQueryReturnBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("s-valid");

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "  ")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Search query is required."));

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "a")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Search query must be at least 2 characters."));

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "a".repeat(65))
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Search query must be 64 characters or fewer."));
    }

    @Test
    void searchShortNumericQueriesReturnBadRequestAndNeverReachDiscord() throws Exception {
        AuthenticatedOperator operator = authorize("s-numeric");

        for (String partialId : new String[] {"1", "12", "300000000000000"}) {
            mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", partialId)
                            .with(oauth2Login().oauth2User(operator)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad_request"))
                    .andExpect(jsonPath("$.message")
                            .value("Numeric search must be a full Discord user id (17-20 digits)."));
        }

        verify(discordClient, org.mockito.Mockito.never()).searchMembers(any());
    }

    @Test
    void searchMissingQueryParameterReturnsBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("s-missing");

        mockMvc.perform(get(searchUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void searchCapsLimitAndDefaultsToTen() throws Exception {
        AuthenticatedOperator operator = authorize("s-limit");

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "art").param("limit", "100")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.results", hasSize(0)));

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "art")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(10));

        ArgumentCaptor<MemberSearchQuery> query = ArgumentCaptor.forClass(MemberSearchQuery.class);
        verify(discordClient, org.mockito.Mockito.atLeastOnce()).searchMembers(query.capture());
        assertThat(query.getAllValues().get(0).limit()).isEqualTo(25);
    }

    @Test
    void searchReturnsEmptyResultsAndWritesNoAuditEvent() throws Exception {
        AuthenticatedOperator operator = authorize("s-empty");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "nomatch")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.query").value("nomatch"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.results", hasSize(0)));

        assertThat(auditRowsFor(guild.registeredGuildId())).isEmpty();
    }

    @Test
    void searchMapsResultsWithoutExposingInternalFields() throws Exception {
        AuthenticatedOperator operator = authorize("s-map");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();
        when(discordClient.searchMembers(any())).thenReturn(new MemberSearchResult(List.of(
                new MemberSearchResultMember(TARGET_USER_ID, "some_user", "Some User", false))));

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", "some")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].*", hasSize(4)))
                .andExpect(jsonPath("$.results[0].userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.results[0].username").value("some_user"))
                .andExpect(jsonPath("$.results[0].displayName").value("Some User"))
                .andExpect(jsonPath("$.results[0].bot").value(false))
                .andExpect(jsonPath("$.results[0].registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.results[0].operatorId").doesNotExist())
                .andExpect(jsonPath("$.results[0].role").doesNotExist());

        assertThat(auditRowsFor(guild.registeredGuildId())).isEmpty();
    }

    @Test
    void searchExactSnowflakeUsesExactIdLookupPath() throws Exception {
        AuthenticatedOperator operator = authorize("s-exact");

        mockMvc.perform(get(searchUrl(GUILD_ID)).param("query", TARGET_USER_ID)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(TARGET_USER_ID));

        ArgumentCaptor<MemberSearchQuery> query = ArgumentCaptor.forClass(MemberSearchQuery.class);
        verify(discordClient).searchMembers(query.capture());
        assertThat(query.getValue().exactIdLookup()).isTrue();
        assertThat(query.getValue().query()).isEqualTo(TARGET_USER_ID);
    }

    private AuthenticatedOperator authorize(String suffix) {
        AuthenticatedOperator operator = newOperator(suffix);
        RegisteredGuildView guild = connectGuild();
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "moderation-op-" + suffix,
                "moderation-" + suffix,
                "Moderation " + suffix,
                "avatar")));
    }

    private RegisteredGuildView connectGuild() {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + GUILD_ID));
        return registeredGuild();
    }

    private RegisteredGuildView registeredGuild() {
        return guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
    }

    private void clearAuditEvents() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_audit_events");
    }

    private List<String> auditRowsFor(UUID registeredGuildId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT event_type || ':' || actor_type || ':' || summary || ':' || target_label
                        FROM guild_os.guild_audit_events
                        WHERE registered_guild_id = ?
                        ORDER BY occurred_at ASC
                        """,
                String.class,
                registeredGuildId);
    }

    private static String url(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/moderation/timeout";
    }

    private static String searchUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/moderation/members/search";
    }

    private static String validBody() {
        return body("""
                "targetUserId":"%s","durationMinutes":10,"reason":"Repeated spam after warning."
                """.formatted(TARGET_USER_ID));
    }

    private static String body(String fields) {
        return "{" + fields.strip() + "}";
    }
}
