package io.github.arturcarletto.guildos.guild;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface GuildRepository extends JpaRepository<Guild, UUID> {

    Optional<Guild> findByDiscordGuildId(String discordGuildId);
}
