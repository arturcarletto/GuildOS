package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static io.github.arturcarletto.guildos.MutableClockTestConfiguration.INITIAL_INSTANT;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false",
        "guildos.activity.processing.enabled=false"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class GuildActivityAnalyticsHttpIntegrationTest {

    private static final String GUILD_ID = "950000000000000001";
    private static final String CHANNEL_ID = "960000000000000001";
    private static final String USER_ID = "970000000000000001";
    private static final String MESSAGE_ID = "980000000000000001";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GuildActivityIngestionService ingestionService;

    @Autowired
    private GuildActivityProcessor processor;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_members");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_channels");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_events");
        accessFixture.clear();
        clock.setInstant(INITIAL_INSTANT);
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void authorizedOperatorReceivesSummaryAndOrderedBuckets() throws Exception {
        AuthenticatedOperator operator = onboardedOperator("authorized");
        ingestAndProcess("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID,
                GuildActivityEventType.MESSAGE_CREATED,
                MESSAGE_ID,
                CHANNEL_ID,
                USER_ID,
                false,
                Instant.parse("2026-07-03T10:05:00Z"));
        ingestAndProcess("MESSAGE_DELETED:" + GUILD_ID + ":" + MESSAGE_ID,
                GuildActivityEventType.MESSAGE_DELETED,
                MESSAGE_ID,
                CHANNEL_ID,
                null,
                null,
                Instant.parse("2026-07-03T11:05:00Z"));

        mockMvc.perform(get(activityUrl(
                        GUILD_ID,
                        "2026-07-03T10:00:00Z",
                        "2026-07-03T12:00:00Z"))
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.bucketTimezone").value("UTC"))
                .andExpect(jsonPath("$.summary.messagesCreated").value(1))
                .andExpect(jsonPath("$.summary.messagesDeleted").value(1))
                .andExpect(jsonPath("$.summary.humanMessages").value(1))
                .andExpect(jsonPath("$.summary.peakHourlyActiveMembers").value(1))
                .andExpect(jsonPath("$.summary.peakHourlyActiveChannels").value(1))
                .andExpect(jsonPath("$.buckets", hasSize(2)))
                .andExpect(jsonPath("$.buckets[0].startedAt").value("2026-07-03T10:00:00Z"))
                .andExpect(jsonPath("$.buckets[1].startedAt").value("2026-07-03T11:00:00Z"))
                .andExpect(jsonPath("$.registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.inboxEventId").doesNotExist())
                .andExpect(jsonPath("$.processingStatus").doesNotExist())
                .andExpect(jsonPath("$.buckets[0].channelId").doesNotExist())
                .andExpect(jsonPath("$.buckets[0].userId").doesNotExist())
                .andExpect(jsonPath("$.buckets[0].messageId").doesNotExist());
    }

    @Test
    void unauthenticatedRequestReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get(activityUrl(
                        GUILD_ID,
                        "2026-07-03T10:00:00Z",
                        "2026-07-03T11:00:00Z")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void otherAndRevokedOperatorsReceiveNonEnumeratingNotFound() throws Exception {
        AuthenticatedOperator authorized = onboardedOperator("owner");
        AuthenticatedOperator other = newOperator("other");

        mockMvc.perform(get(activityUrl(
                        GUILD_ID,
                        "2026-07-03T10:00:00Z",
                        "2026-07-03T11:00:00Z"))
                        .with(oauth2Login().oauth2User(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));

        UUID registeredGuildId = guildDirectory.findByDiscordGuildId(GUILD_ID)
                .orElseThrow()
                .registeredGuildId();
        accessFixture.revoke(authorized.operatorId(), registeredGuildId);

        mockMvc.perform(get(activityUrl(
                        GUILD_ID,
                        "2026-07-03T10:00:00Z",
                        "2026-07-03T11:00:00Z"))
                        .with(oauth2Login().oauth2User(authorized)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    @Test
    void disconnectedButStillAuthorizedGuildRemainsReadable() throws Exception {
        AuthenticatedOperator operator = onboardedOperator("disconnected");
        guildConnectionService.disconnect(new DisconnectGuildCommand(GUILD_ID));

        mockMvc.perform(get(activityUrl(
                        GUILD_ID,
                        "2026-07-03T10:00:00Z",
                        "2026-07-03T11:00:00Z"))
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.messagesCreated").value(0))
                .andExpect(jsonPath("$.buckets", hasSize(0)));
    }

    @Test
    void invalidRangesReturnStableBadRequestJson() throws Exception {
        AuthenticatedOperator operator = onboardedOperator("bad-range");

        assertBadRequest(operator, "not-a-snowflake", "2026-07-03T10:00:00Z", "2026-07-03T11:00:00Z");
        assertBadRequest(operator, GUILD_ID, "2026-07-03T11:00:00Z", "2026-07-03T10:00:00Z");
        assertBadRequest(operator, GUILD_ID, "2026-07-03T10:00:00Z", "2026-08-04T10:00:00Z");

        mockMvc.perform(get("/api/v1/guilds/" + GUILD_ID + "/analytics/activity?from=not-an-instant&to=2026-07-03T11:00:00Z")
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    private void assertBadRequest(
            AuthenticatedOperator operator, String guildId, String from, String to) throws Exception {
        mockMvc.perform(get(activityUrl(guildId, from, to))
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    private void ingestAndProcess(
            String sourceEventId,
            GuildActivityEventType eventType,
            String subjectId,
            String channelId,
            String actorId,
            Boolean actorBot,
            Instant occurredAt) {
        ingestionService.ingest(new IngestGuildActivityCommand(
                sourceEventId,
                eventType,
                GUILD_ID,
                subjectId,
                channelId,
                actorId,
                actorBot,
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION));
        processor.processAvailableBatch();
    }

    private AuthenticatedOperator onboardedOperator(String suffix) {
        AuthenticatedOperator operator = newOperator(suffix);
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + suffix));
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "activity-http-op-" + suffix,
                "activity-http-" + suffix,
                "Activity HTTP " + suffix,
                null)));
    }

    private static String activityUrl(String guildId, String from, String to) {
        return "/api/v1/guilds/" + guildId + "/analytics/activity?from=" + from + "&to=" + to;
    }
}
