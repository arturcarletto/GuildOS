package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;

@Service
class GuildModerationCaseRecorder {

    private final ModerationCaseStore caseStore;
    private final GuildAuditRecorder auditRecorder;
    private final Clock clock;

    GuildModerationCaseRecorder(
            ModerationCaseStore caseStore,
            GuildAuditRecorder auditRecorder,
            Clock clock) {
        this.caseStore = caseStore;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    void recordSuccessfulMemberTimeout(UUID registeredGuildId, UUID operatorId, TimeoutMemberCommand command) {
        Instant now = clock.instant();
        caseStore.append(ModerationCase.memberTimeout(registeredGuildId, command, now));
        auditRecorder.recordOperatorEvent(
                registeredGuildId,
                operatorId,
                GuildAuditEventType.MEMBER_TIMEOUT_CREATED);
    }
}
