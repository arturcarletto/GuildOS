package io.github.arturcarletto.guildos.guildactivity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class GuildActivityProcessorStore {

    private final JdbcTemplate jdbcTemplate;
    private final GuildActivityProjectionStore projectionStore;

    GuildActivityProcessorStore(JdbcTemplate jdbcTemplate, GuildActivityProjectionStore projectionStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.projectionStore = projectionStore;
    }

    @Transactional
    List<GuildActivityEventSnapshot> claimBatch(int batchSize, Instant now, Duration staleLockTimeout) {
        Instant staleBefore = now.minus(staleLockTimeout);
        return jdbcTemplate.query(
                """
                WITH candidates AS (
                    SELECT id
                    FROM guild_os.guild_activity_events
                    WHERE (
                        processing_status = 'PENDING'
                        AND available_at <= ?
                    ) OR (
                        processing_status = 'PROCESSING'
                        AND locked_at <= ?
                    )
                    ORDER BY available_at, received_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE guild_os.guild_activity_events events
                SET processing_status = 'PROCESSING',
                    locked_at = ?,
                    attempt_count = events.attempt_count + 1
                FROM candidates
                WHERE events.id = candidates.id
                RETURNING events.id, events.event_type, events.guild_id, events.subject_discord_id,
                    events.channel_discord_id, events.actor_discord_user_id, events.actor_is_bot,
                    events.occurred_at, events.locked_at, events.attempt_count
                """,
                GuildActivityProcessorStore::mapSnapshot,
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                batchSize,
                Timestamp.from(now));
    }

    @Transactional
    boolean applyProjectionAndMarkProcessed(GuildActivityEventSnapshot event, Instant processedAt) {
        if (!lockActiveClaim(event)) {
            return false;
        }
        projectionStore.apply(event, processedAt);
        int updated = jdbcTemplate.update(
                """
                UPDATE guild_os.guild_activity_events
                SET processing_status = 'PROCESSED',
                    locked_at = NULL,
                    processed_at = ?,
                    available_at = ?,
                    last_failure_category = NULL
                WHERE id = ?
                    AND processing_status = 'PROCESSING'
                    AND attempt_count = ?
                    AND locked_at = ?
                """,
                Timestamp.from(processedAt),
                Timestamp.from(processedAt),
                event.id(),
                event.attemptCount(),
                Timestamp.from(event.lockedAt()));
        if (updated != 1) {
            throw new IllegalStateException("Activity event claim was lost before processed mark");
        }
        return true;
    }

    @Transactional
    boolean recordFailure(
            GuildActivityEventSnapshot event,
            String failureCategory,
            Instant now,
            int maxAttempts,
            Duration initialRetryDelay,
            Duration maxRetryDelay) {
        boolean dead = event.attemptCount() >= maxAttempts;
        Instant availableAt = dead ? now : now.plus(backoff(event.attemptCount(), initialRetryDelay, maxRetryDelay));
        int updated = jdbcTemplate.update(
                """
                UPDATE guild_os.guild_activity_events
                SET processing_status = ?,
                    locked_at = NULL,
                    available_at = ?,
                    processed_at = NULL,
                    last_failure_category = ?
                WHERE id = ?
                    AND processing_status = 'PROCESSING'
                    AND attempt_count = ?
                    AND locked_at = ?
                """,
                dead ? GuildActivityProcessingStatus.DEAD.name() : GuildActivityProcessingStatus.PENDING.name(),
                Timestamp.from(availableAt),
                failureCategory,
                event.id(),
                event.attemptCount(),
                Timestamp.from(event.lockedAt()));
        return dead && updated == 1;
    }

    private boolean lockActiveClaim(GuildActivityEventSnapshot event) {
        List<Integer> rows = jdbcTemplate.query(
                """
                SELECT 1
                FROM guild_os.guild_activity_events
                WHERE id = ?
                    AND processing_status = 'PROCESSING'
                    AND attempt_count = ?
                    AND locked_at = ?
                    AND processed_at IS NULL
                FOR UPDATE
                """,
                (rs, rowNum) -> 1,
                event.id(),
                event.attemptCount(),
                Timestamp.from(event.lockedAt()));
        return !rows.isEmpty();
    }

    private static Duration backoff(int attemptCount, Duration initialRetryDelay, Duration maxRetryDelay) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 30);
        long delayMillis = saturatedMultiply(initialRetryDelay.toMillis(), multiplier);
        return Duration.ofMillis(Math.min(delayMillis, maxRetryDelay.toMillis()));
    }

    private static long saturatedMultiply(long left, long right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static GuildActivityEventSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new GuildActivityEventSnapshot(
                rs.getObject("id", UUID.class),
                GuildActivityEventType.valueOf(rs.getString("event_type")),
                rs.getObject("guild_id", UUID.class),
                rs.getString("subject_discord_id"),
                rs.getString("channel_discord_id"),
                rs.getString("actor_discord_user_id"),
                (Boolean) rs.getObject("actor_is_bot"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("locked_at").toInstant(),
                rs.getInt("attempt_count"));
    }
}
