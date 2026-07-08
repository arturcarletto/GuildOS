package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;
import java.util.UUID;

record GuildActivityEventSnapshot(
        UUID id,
        GuildActivityEventType eventType,
        UUID guildId,
        String subjectDiscordId,
        String channelDiscordId,
        String actorDiscordUserId,
        Boolean actorBot,
        Instant occurredAt,
        Instant lockedAt,
        int attemptCount) {
}
