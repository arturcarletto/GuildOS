package io.github.arturcarletto.guildos.discordchannel;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DiscordGuildChannelRepository extends JpaRepository<DiscordGuildChannel, UUID> {

    Optional<DiscordGuildChannel> findByDiscordGuildIdAndDiscordChannelId(
            String discordGuildId, String discordChannelId);

    @Query("""
            SELECT channel
            FROM DiscordGuildChannel channel
            WHERE channel.discordGuildId = :discordGuildId
                AND channel.active = TRUE
                AND channel.type IN ('TEXT', 'NEWS')
            ORDER BY channel.position ASC, LOWER(channel.name) ASC, channel.discordChannelId ASC
            """)
    List<DiscordGuildChannel> findActiveSupportedByDiscordGuildId(
            @Param("discordGuildId") String discordGuildId);

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.discord_guild_channels (
                id,
                discord_guild_id,
                discord_channel_id,
                name,
                type,
                position,
                active,
                last_synced_at,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :discordGuildId,
                :discordChannelId,
                :name,
                :type,
                :position,
                TRUE,
                :syncedAt,
                :syncedAt,
                :syncedAt
            )
            ON CONFLICT (discord_guild_id, discord_channel_id) DO UPDATE
            SET name = EXCLUDED.name,
                type = EXCLUDED.type,
                position = EXCLUDED.position,
                active = TRUE,
                last_synced_at = EXCLUDED.last_synced_at,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertActive(
            @Param("id") UUID id,
            @Param("discordGuildId") String discordGuildId,
            @Param("discordChannelId") String discordChannelId,
            @Param("name") String name,
            @Param("type") String type,
            @Param("position") int position,
            @Param("syncedAt") Instant syncedAt);

    @Modifying
    @Query("""
            UPDATE DiscordGuildChannel channel
            SET channel.active = FALSE,
                channel.lastSyncedAt = :syncedAt,
                channel.updatedAt = :syncedAt
            WHERE channel.discordGuildId = :discordGuildId
                AND channel.active = TRUE
                AND channel.discordChannelId NOT IN :activeChannelIds
            """)
    int markMissingInactive(
            @Param("discordGuildId") String discordGuildId,
            @Param("activeChannelIds") Collection<String> activeChannelIds,
            @Param("syncedAt") Instant syncedAt);

    @Modifying
    @Query("""
            UPDATE DiscordGuildChannel channel
            SET channel.active = FALSE,
                channel.lastSyncedAt = :syncedAt,
                channel.updatedAt = :syncedAt
            WHERE channel.discordGuildId = :discordGuildId
                AND channel.active = TRUE
            """)
    int markAllInactive(@Param("discordGuildId") String discordGuildId, @Param("syncedAt") Instant syncedAt);
}
