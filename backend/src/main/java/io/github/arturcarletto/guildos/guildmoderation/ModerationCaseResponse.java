package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Instant;

record ModerationCaseResponse(
        String publicCaseId,
        String actionType,
        String targetType,
        String targetUserId,
        Integer durationMinutes,
        String status,
        String summary,
        Instant occurredAt) {

    static ModerationCaseResponse from(ModerationCase moderationCase) {
        return new ModerationCaseResponse(
                moderationCase.getPublicCaseId(),
                moderationCase.getActionType().name(),
                moderationCase.getTargetType().name(),
                moderationCase.getTargetDiscordUserId(),
                moderationCase.getDurationMinutes(),
                moderationCase.getStatus().name(),
                moderationCase.getSummary(),
                moderationCase.getOccurredAt());
    }
}
