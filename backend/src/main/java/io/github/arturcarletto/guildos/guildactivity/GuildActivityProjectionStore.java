package io.github.arturcarletto.guildos.guildactivity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class GuildActivityProjectionStore implements GuildActivityProjectionWriter {

    private final JdbcTemplate jdbcTemplate;

    GuildActivityProjectionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void apply(GuildActivityEventSnapshot event, Instant updatedAt) {
        Instant bucketStart = event.occurredAt().truncatedTo(ChronoUnit.HOURS);
        ensureBucket(event, bucketStart, updatedAt);
        switch (event.eventType()) {
            case MEMBER_JOINED -> increment(event, bucketStart, updatedAt, "member_joined_count", 1);
            case MEMBER_LEFT -> increment(event, bucketStart, updatedAt, "member_left_count", 1);
            case MESSAGE_CREATED -> applyMessageCreated(event, bucketStart, updatedAt);
            case MESSAGE_EDITED -> increment(event, bucketStart, updatedAt, "distinct_message_edited_count", 1);
            case MESSAGE_DELETED -> increment(event, bucketStart, updatedAt, "message_deleted_count", 1);
        }
    }

    private void applyMessageCreated(
            GuildActivityEventSnapshot event, Instant bucketStart, Instant updatedAt) {
        increment(event, bucketStart, updatedAt, "message_created_count", 1);
        if (Boolean.TRUE.equals(event.actorBot())) {
            increment(event, bucketStart, updatedAt, "bot_message_count", 1);
        } else if (Boolean.FALSE.equals(event.actorBot())) {
            increment(event, bucketStart, updatedAt, "human_message_count", 1);
        }
        if (event.actorDiscordUserId() != null && insertDistinctMember(event, bucketStart)) {
            increment(event, bucketStart, updatedAt, "active_member_count", 1);
        }
        if (insertDistinctChannel(event, bucketStart)) {
            increment(event, bucketStart, updatedAt, "active_channel_count", 1);
        }
    }

    private void ensureBucket(GuildActivityEventSnapshot event, Instant bucketStart, Instant updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_activity_hourly (guild_id, bucket_start, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id, bucket_start) DO NOTHING
                """,
                event.guildId(),
                Timestamp.from(bucketStart),
                Timestamp.from(updatedAt));
    }

    private void increment(
            GuildActivityEventSnapshot event,
            Instant bucketStart,
            Instant updatedAt,
            String column,
            long amount) {
        jdbcTemplate.update(
                "UPDATE guild_os.guild_activity_hourly SET "
                        + column + " = " + column + " + ?, updated_at = ? "
                        + "WHERE guild_id = ? AND bucket_start = ?",
                amount,
                Timestamp.from(updatedAt),
                event.guildId(),
                Timestamp.from(bucketStart));
    }

    private boolean insertDistinctMember(GuildActivityEventSnapshot event, Instant bucketStart) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_activity_hourly_members (
                    guild_id, bucket_start, discord_user_id
                ) VALUES (?, ?, ?)
                ON CONFLICT (guild_id, bucket_start, discord_user_id) DO NOTHING
                """,
                event.guildId(),
                Timestamp.from(bucketStart),
                event.actorDiscordUserId());
        return inserted == 1;
    }

    private boolean insertDistinctChannel(GuildActivityEventSnapshot event, Instant bucketStart) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_activity_hourly_channels (
                    guild_id, bucket_start, discord_channel_id
                ) VALUES (?, ?, ?)
                ON CONFLICT (guild_id, bucket_start, discord_channel_id) DO NOTHING
                """,
                event.guildId(),
                Timestamp.from(bucketStart),
                event.channelDiscordId());
        return inserted == 1;
    }
}
