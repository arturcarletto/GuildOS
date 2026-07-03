package io.github.arturcarletto.guildos.guildwelcome;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GuildWelcomeConfigurationRepository
        extends JpaRepository<GuildWelcomeConfiguration, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.guild_welcome_configurations (
                id,
                registered_guild_id,
                enabled,
                channel_id,
                message_template,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :id,
                :registeredGuildId,
                TRUE,
                :channelId,
                :messageTemplate,
                :now,
                :now,
                0
            )
            ON CONFLICT (registered_guild_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("registeredGuildId") UUID registeredGuildId,
            @Param("channelId") String channelId,
            @Param("messageTemplate") String messageTemplate,
            @Param("now") Instant now);

    Optional<GuildWelcomeConfiguration> findByRegisteredGuildId(UUID registeredGuildId);
}
