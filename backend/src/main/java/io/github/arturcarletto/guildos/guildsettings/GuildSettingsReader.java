package io.github.arturcarletto.guildos.guildsettings;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;

/**
 * Read-only settings contract that never materializes defaults or exposes persistence identity.
 */
@Service
public class GuildSettingsReader {

    private final GuildDirectory guildDirectory;
    private final GuildSettingsRepository repository;

    GuildSettingsReader(GuildDirectory guildDirectory, GuildSettingsRepository repository) {
        this.guildDirectory = guildDirectory;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<GuildSettingsView> find(String discordGuildId) {
        return guildDirectory.findByDiscordGuildId(discordGuildId)
                .flatMap(guild -> repository.findByRegisteredGuildId(guild.registeredGuildId()))
                .map(settings -> new GuildSettingsView(
                        settings.getTimezone(),
                        settings.getLocaleTag(),
                        settings.getVersion()));
    }
}
