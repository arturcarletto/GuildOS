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

    private static String validBody() {
        return body("""
                "targetUserId":"%s","durationMinutes":10,"reason":"Repeated spam after warning."
                """.formatted(TARGET_USER_ID));
    }

    private static String body(String fields) {
        return "{" + fields.strip() + "}";
    }
}
