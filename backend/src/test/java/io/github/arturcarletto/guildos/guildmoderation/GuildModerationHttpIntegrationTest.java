package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
    private static final String SECOND_TARGET_USER_ID = "300000000000000917";
    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(1, ChronoUnit.HOURS);

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

    @Autowired
    private MutableTestClock clock;

    @MockitoBean
    private GuildModerationDiscordClient discordClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.setInstant(INSTANT_A);
        jdbcTemplate.update("DELETE FROM guild_os.moderation_cases");
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
        assertThat(caseRowsFor(guild.registeredGuildId()))
                .containsExactly("MEMBER_TIMEOUT_CREATED:DISCORD_USER:" + TARGET_USER_ID
                        + ":10:COMPLETED:Member timeout completed.");
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
        assertThat(caseRowsFor(guild.registeredGuildId())).isEmpty();
    }

    @Test
    void authorizedCaseListReturnsNewestCasesWithoutInternalFields() throws Exception {
        AuthenticatedOperator operator = authorize("cases-read");
        clearAuditEvents();
        clearModerationCases();

        clock.setInstant(INSTANT_A);
        createTimeout(operator, validBody());
        clock.setInstant(INSTANT_B);
        createTimeout(operator, body("""
                "targetUserId":"%s","durationMinutes":20,"reason":"Second raw reason"
                """.formatted(SECOND_TARGET_USER_ID)));

        mockMvc.perform(get(casesUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.cases", hasSize(2)))
                .andExpect(jsonPath("$.cases[0].*", hasSize(8)))
                .andExpect(jsonPath("$.cases[0].publicCaseId").value(startsWith("case_")))
                .andExpect(jsonPath("$.cases[0].actionType").value("MEMBER_TIMEOUT_CREATED"))
                .andExpect(jsonPath("$.cases[0].targetType").value("DISCORD_USER"))
                .andExpect(jsonPath("$.cases[0].targetUserId").value(SECOND_TARGET_USER_ID))
                .andExpect(jsonPath("$.cases[0].durationMinutes").value(20))
                .andExpect(jsonPath("$.cases[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.cases[0].summary").value("Member timeout completed."))
                .andExpect(jsonPath("$.cases[0].occurredAt").value(INSTANT_B.toString()))
                .andExpect(jsonPath("$.cases[0].id").doesNotExist())
                .andExpect(jsonPath("$.cases[0].registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.cases[0].operatorId").doesNotExist())
                .andExpect(jsonPath("$.cases[0].reason").doesNotExist())
                .andExpect(jsonPath("$.cases[0].username").doesNotExist())
                .andExpect(jsonPath("$.cases[0].displayName").doesNotExist())
                .andExpect(jsonPath("$.cases[0].avatar").doesNotExist())
                .andExpect(jsonPath("$.cases[0].rawDiscordPayload").doesNotExist())
                .andExpect(jsonPath("$.cases[0].stackTrace").doesNotExist())
                .andExpect(jsonPath("$.cases[1].targetUserId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.cases[1].occurredAt").value(INSTANT_A.toString()));
    }

    @Test
    void caseListSupportsLimitActionAndTimeFilters() throws Exception {
        AuthenticatedOperator operator = authorize("cases-filters");
        clearAuditEvents();
        clearModerationCases();

        clock.setInstant(INSTANT_A);
        createTimeout(operator, validBody());
        clock.setInstant(INSTANT_B);
        createTimeout(operator, body("""
                "targetUserId":"%s","durationMinutes":20
                """.formatted(SECOND_TARGET_USER_ID)));

        mockMvc.perform(get(casesUrl(GUILD_ID))
                        .param("limit", "1")
                        .param("actionType", "MEMBER_TIMEOUT_CREATED")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(1)))
                .andExpect(jsonPath("$.cases[0].targetUserId").value(SECOND_TARGET_USER_ID));

        mockMvc.perform(get(casesUrl(GUILD_ID))
                        .param("actionType", "MEMBER_TIMEOUT_CREATED")
                        .param("from", INSTANT_A.toString())
                        .param("to", INSTANT_B.toString())
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases", hasSize(1)))
                .andExpect(jsonPath("$.cases[0].targetUserId").value(TARGET_USER_ID));

    }

    @Test
    void caseListRejectsInvalidFiltersWithControlledBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("cases-invalid");

        mockMvc.perform(get(casesUrl(GUILD_ID)).param("limit", "0")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID)).param("limit", "101")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID)).param("limit", "250")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID)).param("actionType", "UNKNOWN")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID))
                        .param("from", INSTANT_B.toString())
                        .param("to", INSTANT_A.toString())
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID)).param("from", "not-an-instant")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void caseListUnauthenticatedMissingUnauthorizedAndRevokedAccessDoNotEnumerateGuilds()
            throws Exception {
        AuthenticatedOperator authorized = authorize("cases-authorized");
        AuthenticatedOperator stranger = newOperator("cases-stranger");

        mockMvc.perform(get(casesUrl(GUILD_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));

        mockMvc.perform(get(casesUrl(MISSING_GUILD_ID)).with(oauth2Login().oauth2User(authorized)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        mockMvc.perform(get(casesUrl(GUILD_ID)).with(oauth2Login().oauth2User(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        RegisteredGuildView guild = registeredGuild();
        accessFixture.revoke(authorized.operatorId(), guild.registeredGuildId());
        mockMvc.perform(get(casesUrl(GUILD_ID)).with(oauth2Login().oauth2User(authorized)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
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

    private void clearModerationCases() {
        jdbcTemplate.update("DELETE FROM guild_os.moderation_cases");
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

    private List<String> caseRowsFor(UUID registeredGuildId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT action_type || ':' || target_type || ':' || target_discord_user_id || ':'
                            || duration_minutes || ':' || status || ':' || summary
                        FROM guild_os.moderation_cases
                        WHERE registered_guild_id = ?
                        ORDER BY occurred_at ASC
                        """,
                String.class,
                registeredGuildId);
    }

    private void createTimeout(AuthenticatedOperator operator, String requestBody) throws Exception {
        mockMvc.perform(post(url(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    private static String url(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/moderation/timeout";
    }

    private static String searchUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/moderation/members/search";
    }

    private static String casesUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/moderation/cases";
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
