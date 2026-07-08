package io.github.arturcarletto.guildos.guildmoderation;

import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;

@Service
class GuildModerationService {

    private final GuildAccessAuthorizer authorizer;
    private final GuildModerationDiscordClient discordClient;
    private final GuildAuditRecorder auditRecorder;

    GuildModerationService(
            GuildAccessAuthorizer authorizer,
            GuildModerationDiscordClient discordClient,
            GuildAuditRecorder auditRecorder) {
        this.authorizer = authorizer;
        this.discordClient = discordClient;
        this.auditRecorder = auditRecorder;
    }

    ModerationActionResponse timeoutMember(
            UUID operatorId,
            String discordGuildId,
            TimeoutMemberRequest request) {
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(ModerationAccessNotFoundException::new);
        TimeoutMemberCommand command = request.toCommand(access.discordGuildId());
        ModerationActionResult result = discordClient.timeoutMember(command);
        auditRecorder.recordOperatorEvent(
                access.registeredGuildId(),
                operatorId,
                GuildAuditEventType.MEMBER_TIMEOUT_CREATED);
        return ModerationActionResponse.memberTimeout(access.discordGuildId(), command, result);
    }
}
