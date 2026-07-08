package io.github.arturcarletto.guildos.guildactivity;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;

@Repository
class GuildActivityIngestionStore {

    private final JdbcTemplate jdbcTemplate;
    private final GuildDirectory guildDirectory;
    private final GuildOnboardingDirectory onboardingDirectory;
    private final Clock clock;

    GuildActivityIngestionStore(
            JdbcTemplate jdbcTemplate,
            GuildDirectory guildDirectory,
            GuildOnboardingDirectory onboardingDirectory,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.guildDirectory = guildDirectory;
        this.onboardingDirectory = onboardingDirectory;
        this.clock = clock;
    }

    @Transactional
    GuildActivityIngestionResult insert(IngestGuildActivityCommand command) {
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(command.discordGuildId())
                .orElse(null);
        if (guild == null) {
            return GuildActivityIngestionResult.IGNORED_UNKNOWN_GUILD;
        }
        if (!onboardingDirectory.isOnboarded(command.discordGuildId())) {
            return GuildActivityIngestionResult.IGNORED_NOT_ONBOARDED;
        }

        Instant receivedAt = clock.instant();
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_activity_events (
                    id, source_event_id, guild_id, event_type, subject_discord_id,
                    channel_discord_id, actor_discord_user_id, actor_is_bot, occurred_at,
                    received_at, schema_version, processing_status, attempt_count, available_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)
                ON CONFLICT (source_event_id) DO NOTHING
                """,
                UUID.randomUUID(),
                command.sourceEventId(),
                guild.registeredGuildId(),
                command.eventType().name(),
                command.subjectDiscordId(),
                command.channelDiscordId(),
                command.actorDiscordUserId(),
                command.actorBot(),
                Timestamp.from(command.occurredAt()),
                Timestamp.from(receivedAt),
                command.schemaVersion(),
                Timestamp.from(receivedAt));
        return inserted == 1
                ? GuildActivityIngestionResult.INSERTED
                : GuildActivityIngestionResult.DUPLICATE;
    }
}
