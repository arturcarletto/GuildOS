package io.github.arturcarletto.guildos.guildaudit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "guild_audit_events", schema = "guild_os")
class GuildAuditEvent {

    @Id
    private UUID id;

    @Column(name = "registered_guild_id", nullable = false, updatable = false)
    private UUID registeredGuildId;

    @Column(name = "operator_id", updatable = false)
    private UUID operatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private GuildAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 32)
    private GuildAuditActorType actorType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "summary", nullable = false, updatable = false, length = 240)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", updatable = false, length = 64)
    private GuildAuditTargetType targetType;

    @Column(name = "target_label", updatable = false, length = 120)
    private String targetLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GuildAuditEvent() {
    }

    private GuildAuditEvent(
            UUID id,
            UUID registeredGuildId,
            UUID operatorId,
            GuildAuditEventType eventType,
            GuildAuditActorType actorType,
            Instant occurredAt,
            String summary,
            GuildAuditTargetType targetType,
            String targetLabel,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.registeredGuildId = Objects.requireNonNull(
                registeredGuildId, "registeredGuildId must not be null");
        this.operatorId = operatorId;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.actorType = Objects.requireNonNull(actorType, "actorType must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.summary = requireBounded(summary, 240, "summary");
        this.targetType = targetType;
        this.targetLabel = targetLabel == null ? null : requireBounded(targetLabel, 120, "targetLabel");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    static GuildAuditEvent create(
            UUID registeredGuildId,
            UUID operatorId,
            GuildAuditActorType actorType,
            GuildAuditEventType eventType,
            Instant occurredAt) {
        return new GuildAuditEvent(
                UUID.randomUUID(),
                registeredGuildId,
                operatorId,
                eventType,
                actorType,
                occurredAt,
                eventType.summary(),
                eventType.targetType(),
                eventType.targetLabel(),
                occurredAt);
    }

    UUID getId() {
        return id;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    UUID getOperatorId() {
        return operatorId;
    }

    GuildAuditEventType getEventType() {
        return eventType;
    }

    GuildAuditActorType getActorType() {
        return actorType;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    String getSummary() {
        return summary;
    }

    GuildAuditTargetType getTargetType() {
        return targetType;
    }

    String getTargetLabel() {
        return targetLabel;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    private static String requireBounded(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be present and bounded");
        }
        return value.trim();
    }
}
