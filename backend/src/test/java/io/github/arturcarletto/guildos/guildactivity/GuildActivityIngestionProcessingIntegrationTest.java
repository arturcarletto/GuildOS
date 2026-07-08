package io.github.arturcarletto.guildos.guildactivity;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false",
        "guildos.activity.processing.enabled=false",
        "guildos.activity.processing.batch-size=10",
        "guildos.activity.processing.initial-retry-delay-ms=1000",
        "guildos.activity.processing.max-retry-delay-ms=4000",
        "guildos.activity.processing.stale-lock-timeout-ms=1000"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class GuildActivityIngestionProcessingIntegrationTest {

    private static final String GUILD_ID = "910000000000000001";
    private static final String CHANNEL_ID = "920000000000000001";
    private static final String USER_ID = "930000000000000001";
    private static final String MESSAGE_ID = "940000000000000001";

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

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_members");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly_channels");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_hourly");
        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_events");
        accessFixture.clear();
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void validOnboardedEventInsertsOnceAndDuplicateIsAtomicNoOp() {
        onboard("dedupe");

        assertThat(ingestionService.ingest(messageCreated("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID)))
                .isEqualTo(GuildActivityIngestionResult.INSERTED);
        assertThat(ingestionService.ingest(messageCreated("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID)))
                .isEqualTo(GuildActivityIngestionResult.DUPLICATE);

        assertThat(count("guild_activity_events")).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateIngestionLeavesOneInboxRow() throws Exception {
        onboard("concurrent-dedupe");
        IngestGuildActivityCommand command = messageCreated("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<GuildActivityIngestionResult> first = executor.submit(() -> ingestAfterSignal(command, ready, start));
            Future<GuildActivityIngestionResult> second = executor.submit(() -> ingestAfterSignal(command, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(
                            GuildActivityIngestionResult.INSERTED,
                            GuildActivityIngestionResult.DUPLICATE);
            assertThat(count("guild_activity_events")).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unknownAndNonOnboardedGuildsAreIgnoredWithoutRows() {
        assertThat(ingestionService.ingest(messageCreated("unknown-source")))
                .isEqualTo(GuildActivityIngestionResult.IGNORED_UNKNOWN_GUILD);

        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild not onboarded"));
        assertThat(ingestionService.ingest(messageCreated("not-onboarded-source")))
                .isEqualTo(GuildActivityIngestionResult.IGNORED_NOT_ONBOARDED);

        assertThat(count("guild_activity_events")).isZero();
    }

    @Test
    void processorProjectsUtcHourlyCountersAndDoesNotDoubleCountDuplicates() {
        onboard("projection");
        Instant eventTime = Instant.parse("2026-07-03T10:59:59Z");
        ingestionService.ingest(new IngestGuildActivityCommand(
                "MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID,
                GuildActivityEventType.MESSAGE_CREATED,
                GUILD_ID,
                MESSAGE_ID,
                CHANNEL_ID,
                USER_ID,
                false,
                eventTime,
                IngestGuildActivityCommand.SCHEMA_VERSION));
        ingestionService.ingest(new IngestGuildActivityCommand(
                "MESSAGE_EDITED:" + GUILD_ID + ":" + MESSAGE_ID,
                GuildActivityEventType.MESSAGE_EDITED,
                GUILD_ID,
                MESSAGE_ID,
                CHANNEL_ID,
                null,
                null,
                Instant.parse("2026-07-03T10:30:00Z"),
                IngestGuildActivityCommand.SCHEMA_VERSION));
        ingestionService.ingest(new IngestGuildActivityCommand(
                "MESSAGE_EDITED:" + GUILD_ID + ":" + MESSAGE_ID,
                GuildActivityEventType.MESSAGE_EDITED,
                GUILD_ID,
                MESSAGE_ID,
                CHANNEL_ID,
                null,
                null,
                Instant.parse("2026-07-03T10:31:00Z"),
                IngestGuildActivityCommand.SCHEMA_VERSION));
        ingestionService.ingest(new IngestGuildActivityCommand(
                "MEMBER_JOINED:" + GUILD_ID + ":" + USER_ID + ":2026-07-03T10:00:00Z",
                GuildActivityEventType.MEMBER_JOINED,
                GUILD_ID,
                USER_ID,
                null,
                USER_ID,
                false,
                Instant.parse("2026-07-03T10:00:00Z"),
                IngestGuildActivityCommand.SCHEMA_VERSION));

        assertThat(processor.processAvailableBatch()).isEqualTo(3);

        assertThat(singleLong("SELECT message_created_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT distinct_message_edited_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT human_message_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT active_member_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT active_channel_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT member_joined_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT bucket_start FROM guild_os.guild_activity_hourly",
                Instant.class)).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM guild_os.guild_activity_events WHERE source_event_id = ?",
                String.class,
                "MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID)).isEqualTo("PROCESSED");
    }

    @Test
    void concurrentProcessorsDoNotDoubleApplyOneInboxEvent() throws Exception {
        onboard("processor-race");
        ingestionService.ingest(messageCreated("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> processAfterSignal(ready, start));
            Future<Integer> second = executor.submit(() -> processAfterSignal(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS))
                    .isEqualTo(1);
            assertThat(singleLong("SELECT message_created_count FROM guild_os.guild_activity_hourly"))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameUserMessagesCountTwoMessagesAndOneActiveMember() throws Exception {
        onboard("same-user");
        Instant occurredAt = Instant.parse("2026-07-03T10:05:00Z");
        ingestionService.ingest(messageCreated(
                "MESSAGE_CREATED:" + GUILD_ID + ":940000000000000101",
                "940000000000000101",
                CHANNEL_ID,
                USER_ID,
                occurredAt));
        ingestionService.ingest(messageCreated(
                "MESSAGE_CREATED:" + GUILD_ID + ":940000000000000102",
                "940000000000000102",
                "920000000000000102",
                USER_ID,
                occurredAt));

        List<GuildActivityEventSnapshot> claimed =
                processorStore.claimBatch(2, INITIAL_INSTANT, Duration.ofSeconds(30));
        assertThat(processConcurrently(claimed))
                .containsExactlyInAnyOrder(
                        GuildActivityProcessingResult.PROCESSED,
                        GuildActivityProcessingResult.PROCESSED);

        assertThat(singleLong("SELECT message_created_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(2);
        assertThat(singleLong("SELECT active_member_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT COUNT(*) FROM guild_os.guild_activity_hourly_members"))
                .isEqualTo(1);
    }

    @Test
    void concurrentSameChannelMessagesCountTwoMessagesAndOneActiveChannel() throws Exception {
        onboard("same-channel");
        Instant occurredAt = Instant.parse("2026-07-03T10:05:00Z");
        ingestionService.ingest(messageCreated(
                "MESSAGE_CREATED:" + GUILD_ID + ":940000000000000201",
                "940000000000000201",
                CHANNEL_ID,
                "930000000000000201",
                occurredAt));
        ingestionService.ingest(messageCreated(
                "MESSAGE_CREATED:" + GUILD_ID + ":940000000000000202",
                "940000000000000202",
                CHANNEL_ID,
                "930000000000000202",
                occurredAt));

        List<GuildActivityEventSnapshot> claimed =
                processorStore.claimBatch(2, INITIAL_INSTANT, Duration.ofSeconds(30));
        assertThat(processConcurrently(claimed))
                .containsExactlyInAnyOrder(
                        GuildActivityProcessingResult.PROCESSED,
                        GuildActivityProcessingResult.PROCESSED);

        assertThat(singleLong("SELECT message_created_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(2);
        assertThat(singleLong("SELECT active_channel_count FROM guild_os.guild_activity_hourly"))
                .isEqualTo(1);
        assertThat(singleLong("SELECT COUNT(*) FROM guild_os.guild_activity_hourly_channels"))
                .isEqualTo(1);
    }

    @Test
    void failureRetryDeadAndStaleClaimRecoveryArePersisted() {
        onboard("failure");
        ingestionService.ingest(messageCreated("retry-source"));

        List<GuildActivityEventSnapshot> claimed =
                processorStore.claimBatch(1, INITIAL_INSTANT, Duration.ofSeconds(30));
        assertThat(claimed).hasSize(1);
        clock.setInstant(INITIAL_INSTANT.plusSeconds(1));

        GuildActivityFailureResult failureResult = processorStore.recordFailure(
                claimed.get(0),
                "ProjectionFailure",
                clock.instant(),
                5,
                Duration.ofSeconds(1),
                Duration.ofSeconds(4));
        assertThat(failureResult).isEqualTo(GuildActivityFailureResult.RETRY_SCHEDULED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM guild_os.guild_activity_events",
                String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_at FROM guild_os.guild_activity_events",
                Instant.class)).isEqualTo(INITIAL_INSTANT.plusSeconds(2));

        List<GuildActivityEventSnapshot> reclaimed =
                processorStore.claimBatch(1, INITIAL_INSTANT.plusSeconds(3), Duration.ofMillis(1));
        assertThat(reclaimed.get(0).attemptCount()).isEqualTo(2);
        failureResult = processorStore.recordFailure(
                reclaimed.get(0),
                "ProjectionFailure",
                INITIAL_INSTANT.plusSeconds(4),
                2,
                Duration.ofSeconds(1),
                Duration.ofSeconds(4));
        assertThat(failureResult).isEqualTo(GuildActivityFailureResult.DEAD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT processing_status FROM guild_os.guild_activity_events",
                String.class)).isEqualTo("DEAD");

        jdbcTemplate.update("DELETE FROM guild_os.guild_activity_events");
        ingestionService.ingest(messageCreated("stale-source"));
        List<GuildActivityEventSnapshot> staleClaim =
                processorStore.claimBatch(1, INITIAL_INSTANT.plusSeconds(5), Duration.ofSeconds(30));
        assertThat(staleClaim).hasSize(1);
        assertThat(processorStore.claimBatch(
                1, INITIAL_INSTANT.plusSeconds(5).plusMillis(999), Duration.ofSeconds(1))).isEmpty();
        List<GuildActivityEventSnapshot> staleReclaimed =
                processorStore.claimBatch(1, INITIAL_INSTANT.plusSeconds(7), Duration.ofSeconds(1));
        assertThat(staleReclaimed).hasSize(1);
        assertThat(staleReclaimed.get(0).attemptCount()).isEqualTo(2);
        assertThat(staleReclaimed.get(0).staleReclaimed()).isTrue();
    }

    @Test
    void schemaRejectsMalformedRowsAndContainsNoContentPayloadColumn() {
        RegisteredGuildView guild = onboard("schema");

        assertRawActivityRejected(guild, "bad-status", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "QUEUED", 0, null, null);
        assertRawActivityRejected(guild, "negative-attempt", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "PENDING", -1, null, null);
        assertRawActivityRejected(guild, "bad-schema", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 2, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "bad-subject", "MESSAGE_DELETED", "not-a-snowflake",
                CHANNEL_ID, null, null, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "bad-channel", "MESSAGE_DELETED", MESSAGE_ID,
                "not-a-channel", null, null, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "bad-actor", "MESSAGE_CREATED", MESSAGE_ID,
                CHANNEL_ID, "not-an-actor", false, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "bad-type", "MESSAGE_REACTION", MESSAGE_ID,
                null, null, null, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "message-without-channel", "MESSAGE_DELETED", MESSAGE_ID,
                null, null, null, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "member-with-channel", "MEMBER_LEFT", USER_ID,
                CHANNEL_ID, USER_ID, false, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "member-without-actor", "MEMBER_JOINED", USER_ID,
                null, null, null, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "edited-with-actor", "MESSAGE_EDITED", MESSAGE_ID,
                CHANNEL_ID, USER_ID, false, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "delete-with-actor", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, USER_ID, false, 1, "PENDING", 0, null, null);
        assertRawActivityRejected(guild, "processing-without-lock", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "PROCESSING", 0, null, null);
        assertRawActivityRejected(guild, "pending-with-lock", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "PENDING", 0, INITIAL_INSTANT, null);
        assertRawActivityRejected(guild, "processed-without-time", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "PROCESSED", 0, null, null);
        assertRawActivityRejected(guild, "pending-with-processed-time", "MESSAGE_DELETED", MESSAGE_ID,
                CHANNEL_ID, null, null, 1, "PENDING", 0, null, INITIAL_INSTANT);

        Integer contentColumns = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'guild_os'
                    AND table_name = 'guild_activity_events'
                    AND (
                        column_name LIKE '%content%'
                        OR column_name LIKE '%payload%'
                        OR data_type IN ('text', 'bytea', 'json', 'jsonb')
                    )
                """,
                Integer.class);
        assertThat(contentColumns).isZero();
    }

    private void assertRawActivityRejected(
            RegisteredGuildView guild,
            String sourceEventId,
            String eventType,
            String subjectId,
            String channelId,
            String actorId,
            Boolean actorBot,
            int schemaVersion,
            String status,
            int attemptCount,
            Instant lockedAt,
            Instant processedAt) {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_activity_events (
                    id, source_event_id, guild_id, event_type, subject_discord_id,
                    channel_discord_id, actor_discord_user_id, actor_is_bot,
                    occurred_at, received_at, schema_version, processing_status,
                    attempt_count, available_at, locked_at, processed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                sourceEventId,
                guild.registeredGuildId(),
                eventType,
                subjectId,
                channelId,
                actorId,
                actorBot,
                Timestamp.from(INITIAL_INSTANT),
                Timestamp.from(INITIAL_INSTANT),
                schemaVersion,
                status,
                attemptCount,
                Timestamp.from(INITIAL_INSTANT),
                lockedAt == null ? null : Timestamp.from(lockedAt),
                processedAt == null ? null : Timestamp.from(processedAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GuildActivityIngestionResult ingestAfterSignal(
            IngestGuildActivityCommand command,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return ingestionService.ingest(command);
    }

    private int processAfterSignal(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return processor.processAvailableBatch();
    }

    private List<GuildActivityProcessingResult> processConcurrently(
            List<GuildActivityEventSnapshot> snapshots) throws Exception {
        assertThat(snapshots).hasSize(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<GuildActivityProcessingResult> first = executor.submit(
                    () -> applyAfterSignal(snapshots.get(0), ready, start));
            Future<GuildActivityProcessingResult> second = executor.submit(
                    () -> applyAfterSignal(snapshots.get(1), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private GuildActivityProcessingResult applyAfterSignal(
            GuildActivityEventSnapshot snapshot,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return processorStore.applyProjectionAndMarkProcessed(snapshot, clock.instant());
    }

    private RegisteredGuildView onboard(String suffix) {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + suffix));
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
        UUID operatorId = operatorLoginService.login(new OperatorLoginCommand(
                "activity-op-" + suffix,
                "activity-" + suffix,
                "Activity " + suffix,
                null)).operatorId();
        accessFixture.authorizeOwner(operatorId, guild.registeredGuildId());
        return guild;
    }

    private IngestGuildActivityCommand messageCreated(String sourceEventId) {
        return messageCreated(sourceEventId, MESSAGE_ID, CHANNEL_ID, USER_ID, clock.instant());
    }

    private IngestGuildActivityCommand messageCreated(
            String sourceEventId,
            String messageId,
            String channelId,
            String userId,
            Instant occurredAt) {
        return new IngestGuildActivityCommand(
                sourceEventId,
                GuildActivityEventType.MESSAGE_CREATED,
                GUILD_ID,
                messageId,
                channelId,
                userId,
                false,
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION);
    }

    private long count(String table) {
        return singleLong("SELECT COUNT(*) FROM guild_os." + table);
    }

    private long singleLong(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
