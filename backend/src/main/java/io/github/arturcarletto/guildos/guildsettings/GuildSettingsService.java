package io.github.arturcarletto.guildos.guildsettings;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
class GuildSettingsService {

    private final GuildSettingsStore store;

    GuildSettingsService(GuildSettingsStore store) {
        this.store = store;
    }

    GuildSettingsResponse get(UUID operatorId, String discordGuildId) {
        return store.get(operatorId, discordGuildId);
    }

    GuildSettingsResponse update(
            UUID operatorId, String discordGuildId, String timezone, String locale, long expectedVersion) {
        NormalizedGuildSettings normalized = GuildSettingsNormalizer.normalize(timezone, locale);
        return store.update(operatorId, discordGuildId, normalized, expectedVersion);
    }
}
