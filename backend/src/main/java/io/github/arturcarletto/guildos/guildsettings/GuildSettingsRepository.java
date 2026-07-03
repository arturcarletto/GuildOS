package io.github.arturcarletto.guildos.guildsettings;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GuildSettingsRepository extends JpaRepository<GuildSettings, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.guild_settings (
                id,
                registered_guild_id,
                timezone,
                locale_tag,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :id,
                :registeredGuildId,
                'UTC',
                'en-US',
                :createdAt,
                :createdAt,
                0
            )
            ON CONFLICT (registered_guild_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("registeredGuildId") UUID registeredGuildId,
            @Param("createdAt") Instant createdAt);

    Optional<GuildSettings> findByRegisteredGuildId(UUID registeredGuildId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT settings FROM GuildSettings settings WHERE settings.registeredGuildId = :registeredGuildId")
    Optional<GuildSettings> findByRegisteredGuildIdForUpdate(
            @Param("registeredGuildId") UUID registeredGuildId);
}
