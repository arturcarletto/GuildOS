package io.github.arturcarletto.guildos.guildsettings;

import java.time.Instant;

record GuildSettingsResponse(
        String guildId,
        String name,
        String timezone,
        String locale,
        long version,
        Instant updatedAt) {
}
