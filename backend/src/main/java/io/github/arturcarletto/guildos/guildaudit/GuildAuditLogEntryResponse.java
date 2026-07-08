package io.github.arturcarletto.guildos.guildaudit;

import java.time.Instant;

record GuildAuditLogEntryResponse(
        Instant occurredAt,
        String eventType,
        String actorType,
        String summary,
        String targetType,
        String targetLabel) {

    static GuildAuditLogEntryResponse from(GuildAuditEvent event) {
        return new GuildAuditLogEntryResponse(
                event.getOccurredAt(),
                event.getEventType().name(),
                event.getActorType().name(),
                event.getSummary(),
                event.getTargetType() == null ? null : event.getTargetType().name(),
                event.getTargetLabel());
    }
}
