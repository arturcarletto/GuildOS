package io.github.arturcarletto.guildos.guildmoderation;

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
@Table(name = "moderation_cases", schema = "guild_os")
class ModerationCase {

    private static final String MEMBER_TIMEOUT_SUMMARY = "Member timeout completed.";

    @Id
    private UUID id;

    @Column(name = "public_case_id", nullable = false, updatable = false, unique = true, length = 40)
    private String publicCaseId;

    @Column(name = "registered_guild_id", nullable = false, updatable = false)
    private UUID registeredGuildId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 64)
    private ModerationCaseActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 64)
    private ModerationCaseTargetType targetType;

    @Column(name = "target_discord_user_id", nullable = false, updatable = false, length = 20)
    private String targetDiscordUserId;

    @Column(name = "duration_minutes", updatable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 32)
    private ModerationCaseStatus status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "summary", nullable = false, updatable = false, length = 240)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationCase() {
    }

    private ModerationCase(
            UUID id,
            String publicCaseId,
            UUID registeredGuildId,
            ModerationCaseActionType actionType,
            ModerationCaseTargetType targetType,
            String targetDiscordUserId,
            Integer durationMinutes,
            ModerationCaseStatus status,
            Instant occurredAt,
            String summary,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.publicCaseId = requireBounded(publicCaseId, 40, "publicCaseId");
        this.registeredGuildId = Objects.requireNonNull(
                registeredGuildId, "registeredGuildId must not be null");
        this.actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetDiscordUserId = requireBounded(targetDiscordUserId, 20, "targetDiscordUserId");
        this.durationMinutes = durationMinutes;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.summary = requireBounded(summary, 240, "summary");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    static ModerationCase memberTimeout(UUID registeredGuildId, TimeoutMemberCommand command, Instant occurredAt) {
        return new ModerationCase(
                UUID.randomUUID(),
                newPublicCaseId(),
                registeredGuildId,
                ModerationCaseActionType.MEMBER_TIMEOUT_CREATED,
                ModerationCaseTargetType.DISCORD_USER,
                command.targetUserId(),
                Math.toIntExact(command.duration().toMinutes()),
                ModerationCaseStatus.COMPLETED,
                occurredAt,
                MEMBER_TIMEOUT_SUMMARY,
                occurredAt);
    }

    String getPublicCaseId() {
        return publicCaseId;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    ModerationCaseActionType getActionType() {
        return actionType;
    }

    ModerationCaseTargetType getTargetType() {
        return targetType;
    }

    String getTargetDiscordUserId() {
        return targetDiscordUserId;
    }

    Integer getDurationMinutes() {
        return durationMinutes;
    }

    ModerationCaseStatus getStatus() {
        return status;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    String getSummary() {
        return summary;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    private static String newPublicCaseId() {
        return "case_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String requireBounded(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be present and bounded");
        }
        return value.trim();
    }
}
