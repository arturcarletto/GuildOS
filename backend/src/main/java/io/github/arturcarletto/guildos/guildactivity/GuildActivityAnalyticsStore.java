package io.github.arturcarletto.guildos.guildactivity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class GuildActivityAnalyticsStore {

    private final JdbcTemplate jdbcTemplate;

    GuildActivityAnalyticsStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    List<GuildActivityAnalyticsBucket> findBuckets(UUID guildId, Instant from, Instant to) {
        return jdbcTemplate.query(
                """
                SELECT bucket_start, message_created_count, distinct_message_edited_count,
                    message_deleted_count, human_message_count, bot_message_count,
                    member_joined_count, member_left_count, active_member_count,
                    active_channel_count
                FROM guild_os.guild_activity_hourly
                WHERE guild_id = ?
                    AND bucket_start >= ?
                    AND bucket_start < ?
                ORDER BY bucket_start ASC
                """,
                GuildActivityAnalyticsStore::mapBucket,
                guildId,
                Timestamp.from(from),
                Timestamp.from(to));
    }

    private static GuildActivityAnalyticsBucket mapBucket(ResultSet rs, int rowNum) throws SQLException {
        return new GuildActivityAnalyticsBucket(
                rs.getTimestamp("bucket_start").toInstant(),
                rs.getLong("message_created_count"),
                rs.getLong("distinct_message_edited_count"),
                rs.getLong("message_deleted_count"),
                rs.getLong("human_message_count"),
                rs.getLong("bot_message_count"),
                rs.getLong("member_joined_count"),
                rs.getLong("member_left_count"),
                rs.getLong("active_member_count"),
                rs.getLong("active_channel_count"));
    }
}
