package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;

interface GuildActivityProjectionWriter {

    void apply(GuildActivityEventSnapshot event, Instant updatedAt);
}
