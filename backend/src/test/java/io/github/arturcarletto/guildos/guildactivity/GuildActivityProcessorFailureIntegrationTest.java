package io.github.arturcarletto.guildos.guildactivity;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static io.github.arturcarletto.guildos.MutableClockTestConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false",
        "guildos.activity.processing.enabled=false",
        "guildos.activity.processing.batch-size=10",
        "guildos.activity.processing.max-attempts=2",
        "guildos.activity.processing.initial-retry-delay-ms=1000",
        "guildos.activity.processing.max-retry-delay-ms=1000",
        "guildos.activity.processing.stale-lock-timeout-ms=1000"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class,
        GuildActivityProcessorFailureIntegrationTest.FailingProjectionConfiguration.class
})
class GuildActivityProcessorFailureIntegrationTest {

    private static final String GUILD_ID = "990000000000000001";
    private static final String CHANNEL_ID = "990000000000000002";
    private static final String USER_ID = "990000000000000003";
    private static final String FAIL_MESSAGE_ID = "990000000000000004";
    private static final String VALID_MESSAGE_ID = "990000000000000005";
    private static final String DEAD_MESSAGE_ID = "990000000000000006";
    private static final String LOST_MESSAGE_ID = "990000000000000007";

    @Autowired
    private GuildActivityIngestionService ingestionService;

    @Autowired
    private GuildActivityProcessor processor;

    @Autowired
    private GuildActivityProcessorStore processorStore;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void reset() {
        ControlledProjectionWriter.clearFailures();
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_members");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_channels");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_events");
        accessFixture.clear();
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void projectionFailureRollsBackProjectionAndSchedulesRetry() {
        onboard("rollback");
        ControlledProjectionWriter.failSubject(FAIL_MESSAGE_ID);
        ingestionService.ingest(messageCreated("fail-rollback", FAIL_MESSAGE_ID));

        assertThat(processor.processAvailableBatch()).isEqualTo(1);

        assertThat(count("guild_activity_hourly")).isZero();
        assertThat(count("guild_activity_hourly_members")).isZero();
        assertThat(count("guild_activity_hourly_channels")).isZero();
        assertThat(eventColumn("fail-rollback", "processing_status", String.class)).isEqualTo("PENDING");
        assertThat(eventColumn("fail-rollback", "locked_at", Instant.class)).isNull();
        assertThat(eventColumn("fail-rollback", "processed_at", Instant.class)).isNull();
        assertThat(eventColumn("fail-rollback", "available_at", Instant.class))
                .isEqualTo(INITIAL_INSTANT.plusSeconds(1));
        assertThat(eventColumn("fail-rollback", "last_failure_category", String.class))
                .isEqualTo("ControlledProjectionFailure")
                .doesNotContain("boom", "\n", "at ");
    }

    @Test
    void oneFailureDoesNotStopLaterBatchEvents() {
        onboard("batch");
        ControlledProjectionWriter.failSubject(FAIL_MESSAGE_ID);
        ingestionService.ingest(messageCreated("fail-batch", FAIL_MESSAGE_ID));
        ingestionService.ingest(messageCreated("valid-batch", VALID_MESSAGE_ID));

        assertThat(processor.processAvailableBatch()).isEqualTo(2);

        assertThat(eventColumn("fail-batch", "processing_status", String.class)).isEqualTo("PENDING");
        assertThat(eventColumn("valid-batch", "processing_status", String.class)).isEqualTo("PROCESSED");
        assertThat(singleLong("SELECT message_created_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT active_member_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT active_channel_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
    }

    @Test
    void deadThroughRealProcessorPathStopsAutomaticClaims() {
        onboard("dead");
        ControlledProjectionWriter.failSubject(DEAD_MESSAGE_ID);
        ingestionService.ingest(messageCreated("fail-dead", DEAD_MESSAGE_ID));

        assertThat(processor.processAvailableBatch()).isEqualTo(1);
        assertThat(eventColumn("fail-dead", "processing_status", String.class)).isEqualTo("PENDING");

        clock.setInstant(INITIAL_INSTANT.plusSeconds(2));
        assertThat(processor.processAvailableBatch()).isEqualTo(1);

        assertThat(eventColumn("fail-dead", "processing_status", String.class)).isEqualTo("DEAD");
        assertThat(eventColumn("fail-dead", "locked_at", Instant.class)).isNull();
        assertThat(eventColumn("fail-dead", "processed_at", Instant.class)).isNull();
        assertThat(eventColumn("fail-dead", "attempt_count", Integer.class)).isEqualTo(2);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(10));
        assertThat(processor.processAvailableBatch()).isZero();
        assertThat(eventColumn("fail-dead", "processing_status", String.class)).isEqualTo("DEAD");
    }

    @Test
    void lostClaimProducesExplicitResultAndMetricWithoutProjectionOrInboxUpdate() {
        onboard("lost");
        ingestionService.ingest(messageCreated("lost-claim", LOST_MESSAGE_ID));
        GuildActivityEventSnapshot snapshot =
                processorStore.claimBatch(1, INITIAL_INSTANT, Duration.ofSeconds(30)).get(0);
        jdbcTemplate.update(
                """
                UPDATE guild_os.guild_activity_events
                SET processing_status = 'PENDING',
                    locked_at = NULL
                WHERE id = ?
                """,
                snapshot.id());
        double before = processingMetric("claim_lost");

        assertThat(processorStore.applyProjectionAndMarkProcessed(snapshot, INITIAL_INSTANT.plusSeconds(1)))
                .isEqualTo(GuildActivityProcessingResult.CLAIM_LOST);
        processor.process(snapshot);

        assertThat(count("guild_activity_hourly")).isZero();
        assertThat(eventColumn("lost-claim", "processing_status", String.class)).isEqualTo("PENDING");
        assertThat(eventColumn("lost-claim", "attempt_count", Integer.class)).isEqualTo(1);
        assertThat(eventColumn("lost-claim", "locked_at", Instant.class)).isNull();
        assertThat(eventColumn("lost-claim", "processed_at", Instant.class)).isNull();
        assertThat(processingMetric("claim_lost")).isEqualTo(before + 1.0d);
    }

    private void onboard(String suffix) {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + suffix));
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
        UUID operatorId = operatorLoginService.login(new OperatorLoginCommand(
                "activity-failure-op-" + suffix,
                "activity-failure-" + suffix,
                "Activity Failure " + suffix,
                null)).operatorId();
        accessFixture.authorizeOwner(operatorId, guild.registeredGuildId());
    }

    private IngestGuildActivityCommand messageCreated(String sourceEventId, String messageId) {
        return new IngestGuildActivityCommand(
                sourceEventId,
                GuildActivityEventType.MESSAGE_CREATED,
                GUILD_ID,
                messageId,
                CHANNEL_ID,
                USER_ID,
                false,
                clock.instant(),
                IngestGuildActivityCommand.SCHEMA_VERSION);
    }

    private long count(String table) {
        return singleLong("SELECT COUNT(*) FROM guild_os." + table);
    }

    private long singleLong(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private <T> T eventColumn(String sourceEventId, String columnName, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM guild_os.guild_activity_events WHERE source_event_id = ?",
                type,
                sourceEventId);
    }

    private double processingMetric(String outcome) {
        var counter = meterRegistry.find("guildos.activity.processing")
                .tag("event_type", "message_created")
                .tag("outcome", outcome)
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingProjectionConfiguration {

        @Bean
        @Primary
        GuildActivityProjectionWriter controlledProjectionWriter(GuildActivityProjectionStore delegate) {
            return new ControlledProjectionWriter(delegate);
        }
    }

    private static final class ControlledProjectionWriter implements GuildActivityProjectionWriter {

        private static final Set<String> failingSubjects = ConcurrentHashMap.newKeySet();

        private final GuildActivityProjectionStore delegate;

        private ControlledProjectionWriter(GuildActivityProjectionStore delegate) {
            this.delegate = delegate;
        }

        static void failSubject(String subjectDiscordId) {
            failingSubjects.add(subjectDiscordId);
        }

        static void clearFailures() {
            failingSubjects.clear();
        }

        @Override
        public void apply(GuildActivityEventSnapshot event, Instant updatedAt) {
            delegate.apply(event, updatedAt);
            if (failingSubjects.contains(event.subjectDiscordId())) {
                throw new ControlledProjectionFailure("projection boom for " + event.subjectDiscordId());
            }
        }
    }

    private static final class ControlledProjectionFailure extends RuntimeException {

        private ControlledProjectionFailure(String message) {
            super(message);
        }
    }
}
