package io.github.arturcarletto.guildos.guildmoderation;

public record ModerationActionResponse(
        String guildId,
        String actionType,
        String targetUserId,
        int durationMinutes,
        String status) {

    static ModerationActionResponse memberTimeout(
            String guildId,
            TimeoutMemberCommand command,
            ModerationActionResult result) {
        return new ModerationActionResponse(
                guildId,
                "MEMBER_TIMEOUT",
                command.targetUserId(),
                Math.toIntExact(command.duration().toMinutes()),
                result.status());
    }
}
