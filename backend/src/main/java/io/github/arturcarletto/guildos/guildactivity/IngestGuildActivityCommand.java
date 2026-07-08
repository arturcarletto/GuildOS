package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;

public record IngestGuildActivityCommand(
        String sourceEventId,
        GuildActivityEventType eventType,
        String discordGuildId,
        String subjectDiscordId,
        String channelDiscordId,
        String actorDiscordUserId,
        Boolean actorBot,
        Instant occurredAt,
        int schemaVersion) {

    public static final int SCHEMA_VERSION = 1;
}
