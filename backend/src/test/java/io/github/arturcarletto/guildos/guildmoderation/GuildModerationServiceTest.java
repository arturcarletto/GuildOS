package io.github.arturcarletto.guildos.guildmoderation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private final GuildModerationCaseRecorder caseRecorder = mock(GuildModerationCaseRecorder.class);
    private final ModerationCaseStore caseStore = mock(ModerationCaseStore.class);
    private final GuildModerationService service =
            new GuildModerationService(authorizer, discordClient, caseRecorder, caseStore);

    @Test
    void successfulTimeoutCallsDiscordOutsideTransactionThenRecordsCaseAndAudit() {
        allowAccess();
        when(discordClient.timeoutMember(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            verify(caseRecorder, never()).recordSuccessfulMemberTimeout(any(), any(), any());
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
        verify(caseRecorder).recordSuccessfulMemberTimeout(
                REGISTERED_GUILD_ID,
                OPERATOR_ID,
                new TimeoutMemberCommand(
                        GUILD_ID,
                        "800000000000000001",
                        java.time.Duration.ofMinutes(10),
                        Optional.of("Repeated spam")));
    }

    @Test
    void discordFailureDoesNotRecordCaseOrAuditSuccess() {
        allowAccess();
        when(discordClient.timeoutMember(any()))
                .thenThrow(new ModerationDiscordActionException(ModerationFailureCategory.BOT_PERMISSION_MISSING));

        assertThatThrownBy(() -> service.timeoutMember(
                OPERATOR_ID,
                GUILD_ID,
                request("800000000000000001", 10, "Repeated spam")))
                .isInstanceOf(ModerationDiscordActionException.class);

        verify(caseRecorder, never()).recordSuccessfulMemberTimeout(any(), any(), any());
    }

    @Test
    void searchEnforcesAuthorizationBoundaryBeforeCallingDiscord() {
        when(authorizer.findActive(OPERATOR_ID, GUILD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchMembers(OPERATOR_ID, GUILD_ID, "art", null))
                .isInstanceOf(ModerationAccessNotFoundException.class);

        verify(discordClient, never()).searchMembers(any());
        verify(caseRecorder, never()).recordSuccessfulMemberTimeout(any(), any(), any());
    }

    @Test
    void searchValidatesQueryBeforeCallingDiscord() {
        allowAccess();

        assertThatThrownBy(() -> service.searchMembers(OPERATOR_ID, GUILD_ID, "   ", null))
                .isInstanceOf(InvalidModerationActionException.class);

        verify(discordClient, never()).searchMembers(any());
    }

    @Test
    void searchCallsDiscordOutsideTransactionAndNeverAudits() {
        allowAccess();
        when(discordClient.searchMembers(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            MemberSearchQuery query = invocation.getArgument(0);
            assertThat(query.discordGuildId()).isEqualTo(GUILD_ID);
            assertThat(query.query()).isEqualTo("art");
            assertThat(query.exactIdLookup()).isFalse();
            assertThat(query.limit()).isEqualTo(25);
            return new MemberSearchResult(List.of(
                    new MemberSearchResultMember("800000000000000001", "some_user", "Some User", false)));
        });

        MemberSearchResponse response = service.searchMembers(OPERATOR_ID, GUILD_ID, " art ", 100);

        assertThat(response.guildId()).isEqualTo(GUILD_ID);
        assertThat(response.query()).isEqualTo("art");
        assertThat(response.limit()).isEqualTo(25);
        assertThat(response.results()).singleElement()
                .satisfies(member -> assertThat(member.userId()).isEqualTo("800000000000000001"));
        verify(caseRecorder, never()).recordSuccessfulMemberTimeout(any(), any(), any());
    }

    @Test
    void searchAdapterFailureIsPropagatedAndNeverAudits() {
        allowAccess();
        when(discordClient.searchMembers(any()))
                .thenThrow(new ModerationDiscordActionException(ModerationFailureCategory.GUILD_UNAVAILABLE));

        assertThatThrownBy(() -> service.searchMembers(OPERATOR_ID, GUILD_ID, "art", null))
                .isInstanceOf(ModerationDiscordActionException.class);

        verify(caseRecorder, never()).recordSuccessfulMemberTimeout(any(), any(), any());
    }

    @Test
    void listCasesReusesAuthorizationBoundaryBeforeReadingHistory() {
        when(authorizer.findActive(OPERATOR_ID, GUILD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCases(OPERATOR_ID, GUILD_ID, null, null, null, null))
                .isInstanceOf(ModerationAccessNotFoundException.class);

        verify(caseStore, never()).find(any(), any(), any(), any(), anyInt());
    }

    @Test
    void listCasesValidatesFiltersBeforeReadingHistory() {
        assertThatThrownBy(() -> service.listCases(OPERATOR_ID, GUILD_ID, 0, null, null, null))
                .isInstanceOf(InvalidModerationActionException.class);
        assertThatThrownBy(() -> service.listCases(OPERATOR_ID, GUILD_ID, null, "UNKNOWN", null, null))
                .isInstanceOf(InvalidModerationActionException.class);

        verify(caseStore, never()).find(any(), any(), any(), any(), anyInt());
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
