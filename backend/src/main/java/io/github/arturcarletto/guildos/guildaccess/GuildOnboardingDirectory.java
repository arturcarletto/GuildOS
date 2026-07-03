package io.github.arturcarletto.guildos.guildaccess;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;

/**
 * Read-only capability contract answering whether a registered guild has at least one active
 * Guild OS onboarding authorization. It exposes no operator identity, role, count, or entity.
 */
@Service
public class GuildOnboardingDirectory {

    private final GuildDirectory guildDirectory;
    private final OperatorGuildAccessRepository repository;

    GuildOnboardingDirectory(
            GuildDirectory guildDirectory,
            OperatorGuildAccessRepository repository) {
        this.guildDirectory = guildDirectory;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isOnboarded(String discordGuildId) {
        String validatedGuildId = DiscordSnowflakes.requireValid(discordGuildId);
        return guildDirectory.findByDiscordGuildId(validatedGuildId)
                .map(guild -> repository.existsByRegisteredGuildIdAndRevokedAtIsNull(
                        guild.registeredGuildId()))
                .orElse(false);
    }
}
