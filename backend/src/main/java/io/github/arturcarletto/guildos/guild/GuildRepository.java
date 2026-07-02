package io.github.arturcarletto.guildos.guild;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GuildRepository extends JpaRepository<Guild, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.guilds (
                id,
                discord_guild_id,
                guild_name,
                connection_status,
                first_connected_at,
                last_connected_at,
                disconnected_at,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :id,
                :discordGuildId,
                :guildName,
                'CONNECTED',
                :connectedAt,
                :connectedAt,
                NULL,
                :connectedAt,
                :connectedAt,
                0
            )
            ON CONFLICT (discord_guild_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("discordGuildId") String discordGuildId,
            @Param("guildName") String guildName,
            @Param("connectedAt") Instant connectedAt);

    Optional<Guild> findByDiscordGuildId(String discordGuildId);
}
