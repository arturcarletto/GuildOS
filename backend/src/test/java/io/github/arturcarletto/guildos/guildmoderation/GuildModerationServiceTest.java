package io.github.arturcarletto.guildos.guildmoderation;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildModerationServiceTest {

    private static final UUID OPERATOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REGISTERED_GUILD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String GUILD_ID = "700000000000000001";

    private final GuildAccessAuthorizer authorizer = mock(GuildAccessAuthorizer.class);
    private final GuildModerationDiscordClient discordClient = mock(GuildModerationDiscordClient.class);
    private final GuildAuditRecorder auditRecorder = mock(GuildAuditRecorder.class);
    private final GuildModerationService service =
            new GuildModerationService(authorizer, discordClient, auditRecorder);

    @Test
    void successfulTimeoutCallsDiscordOutsideTransactionThenAudits() {
        allowAccess();
        when(discordClient.timeoutMember(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            TimeoutMemberCommand command = invocation.getArgument(0);
            assertThat(command.discordGuildId()).isEqualTo(GUILD_ID);
            assertThat(command.targetUserId()).isEqualTo("800000000000000001");
            assertThat(command.reason()).contains("Repeated spam");
            return ModerationActionResult.success();
        });

        ModerationActionResponse response = service.timeoutMember(
                OPERATOR_ID,
                GUILD_ID,
                request("800000000000000001", 10, " Repeated spam "));

        assertThat(response.actionType()).isEqualTo("MEMBER_TIMEOUT");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        verify(auditRecorder).recordOperatorEvent(
                REGISTERED_GUILD_ID,
                OPERATOR_ID,
                GuildAuditEventType.MEMBER_TIMEOUT_CREATED);
    }

    @Test
    void discordFailureDoesNotAuditSuccess() {
        allowAccess();
        when(discordClient.timeoutMember(any()))
                .thenThrow(new ModerationDiscordActionException(ModerationFailureCategory.BOT_PERMISSION_MISSING));

        assertThatThrownBy(() -> service.timeoutMember(
                OPERATOR_ID,
                GUILD_ID,
                request("800000000000000001", 10, "Repeated spam")))
                .isInstanceOf(ModerationDiscordActionException.class);

        verify(auditRecorder, never()).recordOperatorEvent(any(), any(), any());
    }

    private void allowAccess() {
        when(authorizer.findActive(OPERATOR_ID, GUILD_ID)).thenReturn(Optional.of(new AuthorizedGuildAccess(
                REGISTERED_GUILD_ID,
                GUILD_ID,
                "Guild",
                "OWNER",
                true)));
    }

    private static TimeoutMemberRequest request(String targetUserId, int durationMinutes, String reason) {
        return new TimeoutMemberRequest(targetUserId, durationMinutes, reason);
    }
}
