package io.github.arturcarletto.guildos.guildaudit;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class GuildAuditRecorder {

    private final GuildAuditStore store;

    GuildAuditRecorder(GuildAuditStore store) {
        this.store = store;
    }

    public void recordOperatorEvent(UUID registeredGuildId, UUID operatorId, GuildAuditEventType eventType) {
        store.append(
                Objects.requireNonNull(registeredGuildId, "registeredGuildId must not be null"),
                Objects.requireNonNull(operatorId, "operatorId must not be null"),
                GuildAuditActorType.OPERATOR,
                Objects.requireNonNull(eventType, "eventType must not be null"));
    }

    public void recordSystemEvent(UUID registeredGuildId, GuildAuditEventType eventType) {
        store.append(
                Objects.requireNonNull(registeredGuildId, "registeredGuildId must not be null"),
                null,
                GuildAuditActorType.SYSTEM,
                Objects.requireNonNull(eventType, "eventType must not be null"));
    }

    public void recordDiscordEvent(UUID registeredGuildId, GuildAuditEventType eventType) {
        store.append(
                Objects.requireNonNull(registeredGuildId, "registeredGuildId must not be null"),
                null,
                GuildAuditActorType.DISCORD,
                Objects.requireNonNull(eventType, "eventType must not be null"));
    }
}
