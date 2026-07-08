package io.github.arturcarletto.guildos.guildaudit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSnapshot;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSyncService;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelType;
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
class GuildAuditLogHttpIntegrationTest {

    private static final String GUILD_ID = "200000000000000777";
    private static final String MISSING_GUILD_ID = "200000000000000778";
    private static final String CHANNEL_ID = "300000000000000777";
    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(1, ChronoUnit.HOURS);
    private static final Instant INSTANT_C = INSTANT_A.plus(2, ChronoUnit.HOURS);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GuildAuditRecorder auditRecorder;

    @Autowired
    private DiscordGuildChannelSyncService channelSyncService;

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
        clock.setInstant(INSTANT_A);
        jdbcTemplate.update("DELETE FROM guild_os.guild_audit_events");
        jdbcTemplate.update("DELETE FROM guild_os.discord_guild_channels");
        jdbcTemplate.update("DELETE FROM guild_os.guild_member_message_configurations");
        jdbcTemplate.update("DELETE FROM guild_os.guild_settings");
        accessFixture.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void authorizedReadReturnsNewestAuditEventsWithoutInternalFields() throws Exception {
        AuthenticatedOperator operator = authorize("read-shape");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();
        record(guild, operator, GuildAuditEventType.GUILD_SETTINGS_UPDATED, INSTANT_A);
        recordSystem(guild, GuildAuditEventType.CHANNEL_METADATA_SYNCED, INSTANT_B);
        recordDiscord(guild, GuildAuditEventType.WELCOME_CONFIGURED, INSTANT_C);

        mockMvc.perform(get(auditUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[0].occurredAt").value(INSTANT_C.toString()))
                .andExpect(jsonPath("$.events[0].eventType").value("WELCOME_CONFIGURED"))
                .andExpect(jsonPath("$.events[0].actorType").value("DISCORD"))
                .andExpect(jsonPath("$.events[0].summary")
                        .value("Welcome message configuration was updated."))
                .andExpect(jsonPath("$.events[0].targetType").value("WELCOME_MESSAGE"))
                .andExpect(jsonPath("$.events[0].targetLabel").value("Welcome automation"))
                .andExpect(jsonPath("$.events[0].id").doesNotExist())
                .andExpect(jsonPath("$.events[0].registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.events[0].operatorId").doesNotExist())
                .andExpect(jsonPath("$.events[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.events[0].sessionId").doesNotExist())
                .andExpect(jsonPath("$.events[0].stackTrace").doesNotExist());
    }

    @Test
    void defaultAndMaxLimitsAreEnforced() throws Exception {
        AuthenticatedOperator operator = authorize("limits");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();
        for (int index = 0; index < 105; index++) {
            record(
                    guild,
                    operator,
                    GuildAuditEventType.GUILD_SETTINGS_UPDATED,
                    INSTANT_A.plus(index, ChronoUnit.MINUTES));
        }

        mockMvc.perform(get(auditUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(50)));

        mockMvc.perform(get(auditUrl(GUILD_ID) + "?limit=250")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(100)));

        mockMvc.perform(get(auditUrl(GUILD_ID) + "?limit=0")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void eventTypeFromAndToFiltersAreApplied() throws Exception {
        AuthenticatedOperator operator = authorize("filters");
        RegisteredGuildView guild = registeredGuild();
        clearAuditEvents();
        record(guild, operator, GuildAuditEventType.GUILD_SETTINGS_UPDATED, INSTANT_A);
        record(guild, operator, GuildAuditEventType.WELCOME_CONFIGURED, INSTANT_B);
        record(guild, operator, GuildAuditEventType.WELCOME_CONFIGURED, INSTANT_C);

        mockMvc.perform(get(auditUrl(GUILD_ID)
                        + "?eventType=WELCOME_CONFIGURED&from=" + INSTANT_B + "&to=" + INSTANT_C)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].eventType").value("WELCOME_CONFIGURED"))
                .andExpect(jsonPath("$.events[0].occurredAt").value(INSTANT_B.toString()));

        mockMvc.perform(get(auditUrl(GUILD_ID) + "?eventType=UNKNOWN")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));

        mockMvc.perform(get(auditUrl(GUILD_ID) + "?from=" + INSTANT_C + "&to=" + INSTANT_B)
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void unauthenticatedMissingAndUnauthorizedReadsDoNotEnumerateGuilds() throws Exception {
        AuthenticatedOperator operator = authorize("authorized");
        AuthenticatedOperator stranger = newOperator("stranger");

        mockMvc.perform(get(auditUrl(GUILD_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));

        mockMvc.perform(get(auditUrl(GUILD_ID)).with(oauth2Login().oauth2User(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        mockMvc.perform(get(auditUrl(MISSING_GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        mockMvc.perform(get(auditUrl("not-a-snowflake")).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void settingsWelcomeGoodbyeAndAccessMutationsAreRecorded() throws Exception {
        AuthenticatedOperator operator = newOperator("recording");
        RegisteredGuildView guild = connectGuild();

        clock.setInstant(INSTANT_A);
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        clock.setInstant(INSTANT_A.plus(10, ChronoUnit.MINUTES));
        accessFixture.revoke(operator.operatorId(), guild.registeredGuildId());
        clock.setInstant(INSTANT_A.plus(20, ChronoUnit.MINUTES));
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());

        assertThat(auditTypesFor(guild.registeredGuildId()))
                .containsExactly(
                        "GUILD_ONBOARDING_CREATED:OPERATOR",
                        "GUILD_ACCESS_REVOKED:OPERATOR",
                        "GUILD_ONBOARDING_REACTIVATED:OPERATOR");
        clearAuditEvents();

        mockMvc.perform(get("/api/v1/guilds/" + GUILD_ID + "/settings")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk());
        clock.setInstant(INSTANT_B);
        mockMvc.perform(put("/api/v1/guilds/" + GUILD_ID + "/settings")
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Europe/London\",\"locale\":\"en-GB\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        clock.setInstant(INSTANT_B.plus(10, ChronoUnit.MINUTES));
        mockMvc.perform(put(memberMessageUrl("welcome"))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberMessageBody("Welcome {member}", "Welcome to {server}")))
                .andExpect(status().isOk());
        clock.setInstant(INSTANT_B.plus(20, ChronoUnit.MINUTES));
        mockMvc.perform(post(memberMessageUrl("welcome") + "/toggle")
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf()))
                .andExpect(status().isOk());
        clock.setInstant(INSTANT_B.plus(30, ChronoUnit.MINUTES));
        mockMvc.perform(put(memberMessageUrl("goodbye"))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelId\":\"" + CHANNEL_ID + "\",\"message\":\"{member} left {server}\"}"))
                .andExpect(status().isOk());
        clock.setInstant(INSTANT_B.plus(40, ChronoUnit.MINUTES));
        mockMvc.perform(post(memberMessageUrl("goodbye") + "/toggle")
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(auditTypesFor(guild.registeredGuildId()))
                .containsExactly(
                        "GUILD_SETTINGS_UPDATED:OPERATOR",
                        "WELCOME_CONFIGURED:OPERATOR",
                        "WELCOME_TOGGLED:OPERATOR",
                        "GOODBYE_CONFIGURED:OPERATOR",
                        "GOODBYE_TOGGLED:OPERATOR");

        clearAuditEvents();
        clock.setInstant(INSTANT_C);
        accessFixture.revoke(operator.operatorId(), guild.registeredGuildId());
        clock.setInstant(INSTANT_C.plus(10, ChronoUnit.MINUTES));
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());

        assertThat(auditTypesFor(guild.registeredGuildId()))
                .containsExactly(
                        "GUILD_ACCESS_REVOKED:OPERATOR",
                        "GUILD_ONBOARDING_REACTIVATED:OPERATOR");
    }

    @Test
    void channelMetadataSyncRecordsOnlyWhenActiveSupportedMetadataChanges() {
        RegisteredGuildView guild = connectGuild();
        clearAuditEvents();

        clock.setInstant(INSTANT_A);
        channelSyncService.syncGuildChannels(GUILD_ID, List.of(channel("general", 1)));
        clock.setInstant(INSTANT_B);
        channelSyncService.syncGuildChannels(GUILD_ID, List.of(channel("general", 1)));
        clock.setInstant(INSTANT_C);
        channelSyncService.syncGuildChannels(GUILD_ID, List.of(channel("announcements", 1)));

        assertThat(auditTypesFor(guild.registeredGuildId()))
                .containsExactly(
                        "CHANNEL_METADATA_SYNCED:SYSTEM",
                        "CHANNEL_METADATA_SYNCED:SYSTEM");
    }

    private AuthenticatedOperator authorize(String suffix) {
        AuthenticatedOperator operator = newOperator(suffix);
        RegisteredGuildView guild = connectGuild();
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "audit-op-" + suffix,
                "audit-" + suffix,
                "Audit " + suffix,
                "avatar")));
    }

    private RegisteredGuildView connectGuild() {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + GUILD_ID));
        return registeredGuild();
    }

    private RegisteredGuildView registeredGuild() {
        return guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
    }

    private void record(
            RegisteredGuildView guild,
            AuthenticatedOperator operator,
            GuildAuditEventType eventType,
            Instant occurredAt) {
        clock.setInstant(occurredAt);
        auditRecorder.recordOperatorEvent(guild.registeredGuildId(), operator.operatorId(), eventType);
    }

    private void recordSystem(RegisteredGuildView guild, GuildAuditEventType eventType, Instant occurredAt) {
        clock.setInstant(occurredAt);
        auditRecorder.recordSystemEvent(guild.registeredGuildId(), eventType);
    }

    private void recordDiscord(RegisteredGuildView guild, GuildAuditEventType eventType, Instant occurredAt) {
        clock.setInstant(occurredAt);
        auditRecorder.recordDiscordEvent(guild.registeredGuildId(), eventType);
    }

    private void clearAuditEvents() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_audit_events");
    }

    private List<String> auditTypesFor(UUID registeredGuildId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT event_type || ':' || actor_type
                        FROM guild_os.guild_audit_events
                        WHERE registered_guild_id = ?
                        ORDER BY occurred_at ASC
                        """,
                String.class,
                registeredGuildId);
    }

    private static String auditUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/audit-log";
    }

    private static String memberMessageUrl(String kind) {
        return "/api/v1/guilds/" + GUILD_ID + "/member-messages/" + kind;
    }

    private static String memberMessageBody(String title, String message) {
        return """
                {"channelId":"%s","title":"%s","message":"%s"}
                """.formatted(CHANNEL_ID, title, message);
    }

    private static DiscordGuildChannelSnapshot channel(String name, int position) {
        return new DiscordGuildChannelSnapshot(CHANNEL_ID, name, DiscordGuildChannelType.TEXT, position);
    }
}
