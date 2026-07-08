package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildMemberMessageServiceTest {

    private static final String GUILD_ID = "700000000000000001";
    private static final UUID REGISTERED_GUILD_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final MemberMessageKind KIND = MemberMessageKind.WELCOME;

    private final GuildDirectory guildDirectory = mock(GuildDirectory.class);
    private final GuildOnboardingDirectory onboardingDirectory = mock(GuildOnboardingDirectory.class);
    private final GuildMemberMessageStore store = mock(GuildMemberMessageStore.class);
    private final GuildAuditRecorder auditRecorder = mock(GuildAuditRecorder.class);
    private final GuildMemberMessageService service =
            new GuildMemberMessageService(guildDirectory, onboardingDirectory, store, auditRecorder);

    @Test
    void unknownAndDisconnectedGuildsAreUnavailable() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.empty());
        assertThat(service.status(GUILD_ID, KIND).state()).isEqualTo(MemberMessageState.UNAVAILABLE);

        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.of(guild(false)));
        assertThat(service.status(GUILD_ID, KIND).state()).isEqualTo(MemberMessageState.UNAVAILABLE);
        verify(store, never()).find(any(), any());
    }

    @Test
    void connectedNonOnboardedGuildRequiresOnboarding() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.of(guild(true)));
        when(onboardingDirectory.isOnboarded(GUILD_ID)).thenReturn(false);

        assertThat(service.status(GUILD_ID, KIND).state())
                .isEqualTo(MemberMessageState.ONBOARDING_REQUIRED);
    }

    @Test
    void configureFromAbsentSnapshotCreatesWithoutTouchingUpdate() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.empty());
        when(store.createIfAbsent(eq(REGISTERED_GUILD_ID), eq(KIND), eq("100"), any()))
                .thenReturn(GuildMemberMessageMutationResult.created(stored(true, 0)));

        GuildMemberMessageView view = service.configure(GUILD_ID, KIND, command());

        assertThat(view.state()).isEqualTo(MemberMessageState.CONFIGURED);
        assertThat(view.enabled()).isTrue();
        verify(auditRecorder).recordDiscordEvent(REGISTERED_GUILD_ID, GuildAuditEventType.WELCOME_CONFIGURED);
        verify(store, never()).configureExisting(any(), any(), anyString(), any(), anyLong());
    }

    @Test
    void configureFromPresentSnapshotUpdatesAgainstObservedVersion() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(false, 4)));
        when(store.configureExisting(eq(REGISTERED_GUILD_ID), eq(KIND), eq("100"), any(), eq(4L)))
                .thenReturn(GuildMemberMessageMutationResult.updated(stored(false, 5)));

        service.configure(GUILD_ID, KIND, command());

        verify(store).configureExisting(eq(REGISTERED_GUILD_ID), eq(KIND), eq("100"), any(), eq(4L));
        verify(auditRecorder).recordDiscordEvent(REGISTERED_GUILD_ID, GuildAuditEventType.WELCOME_CONFIGURED);
        verify(store, never()).createIfAbsent(any(), any(), anyString(), any());
    }

    @Test
    void configureFromPresentSnapshotDoesNotAuditWhenStoreReportsUnchanged() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(false, 4)));
        when(store.configureExisting(eq(REGISTERED_GUILD_ID), eq(KIND), eq("100"), any(), eq(4L)))
                .thenReturn(GuildMemberMessageMutationResult.unchanged(stored(false, 4)));

        service.configure(GUILD_ID, KIND, command());

        verify(auditRecorder, never()).recordDiscordEvent(any(), any());
    }

    @Test
    void configureLostCreateRaceAndOptimisticFailureBecomeConflicts() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.empty());
        when(store.createIfAbsent(eq(REGISTERED_GUILD_ID), eq(KIND), eq("100"), any()))
                .thenThrow(new GuildMemberMessageConflictException());
        assertThatThrownBy(() -> service.configure(GUILD_ID, KIND, command()))
                .isInstanceOf(GuildMemberMessageConflictException.class);

        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(true, 1)));
        when(store.configureExisting(any(), any(), anyString(), any(), anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        GuildMemberMessageConfiguration.class, REGISTERED_GUILD_ID));
        assertThatThrownBy(() -> service.configure(GUILD_ID, KIND, command()))
                .isInstanceOf(GuildMemberMessageConflictException.class);
    }

    @Test
    void previewRendersConfiguredStateWithoutWriting() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(true, 2)));

        GuildMemberMessageView view = service.preview(
                GUILD_ID, KIND, new MemberMessageRenderContext("Artur", "artur", "Heaven", 42, "<@1>"));

        assertThat(view.renderedPreview()).isNotNull();
        assertThat(view.renderedPreview().description()).isEqualTo("Hi Artur");
        verify(store, never()).configureExisting(any(), any(), anyString(), any(), anyLong());
        verify(store, never()).toggleExisting(any(), any(), anyLong());
    }

    @Test
    void toggleRequiresAnExistingConfiguration() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.empty());

        assertThat(service.toggle(GUILD_ID, KIND).state()).isEqualTo(MemberMessageState.NOT_CONFIGURED);
        verify(store, never()).toggleExisting(any(), any(), anyLong());
    }

    @Test
    void togglePassesTheObservedVersion() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(true, 5)));
        when(store.toggleExisting(REGISTERED_GUILD_ID, KIND, 5))
                .thenReturn(GuildMemberMessageMutationResult.disabled(stored(false, 6)));

        GuildMemberMessageView view = service.toggle(GUILD_ID, KIND);

        assertThat(view.enabled()).isFalse();
        verify(store).toggleExisting(REGISTERED_GUILD_ID, KIND, 5);
        verify(auditRecorder).recordDiscordEvent(REGISTERED_GUILD_ID, GuildAuditEventType.WELCOME_TOGGLED);
    }

    @Test
    void resolveDeliveryReportsEveryDecision() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.empty());
        assertThat(service.resolveDelivery(GUILD_ID, KIND).decision())
                .isEqualTo(MemberMessageDeliveryPlan.Decision.UNAVAILABLE);

        allowAccess();
        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.empty());
        assertThat(service.resolveDelivery(GUILD_ID, KIND).decision())
                .isEqualTo(MemberMessageDeliveryPlan.Decision.NOT_CONFIGURED);

        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(false, 1)));
        assertThat(service.resolveDelivery(GUILD_ID, KIND).decision())
                .isEqualTo(MemberMessageDeliveryPlan.Decision.DISABLED);

        when(store.find(REGISTERED_GUILD_ID, KIND)).thenReturn(Optional.of(stored(true, 1)));
        MemberMessageDeliveryPlan plan = service.resolveDelivery(GUILD_ID, KIND);
        assertThat(plan.decision()).isEqualTo(MemberMessageDeliveryPlan.Decision.DELIVER);
        assertThat(plan.channelId()).isEqualTo("100");
        assertThat(plan.appearance()).isNotNull();
    }

    private void allowAccess() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.of(guild(true)));
        when(onboardingDirectory.isOnboarded(GUILD_ID)).thenReturn(true);
    }

    private RegisteredGuildView guild(boolean connected) {
        return new RegisteredGuildView(REGISTERED_GUILD_ID, GUILD_ID, "Heaven", connected);
    }

    private static ConfigureMemberMessageCommand command() {
        return new ConfigureMemberMessageCommand(
                "100", "Hi {member}",
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static StoredGuildMemberMessage stored(boolean enabled, long version) {
        return new StoredGuildMemberMessage(
                KIND,
                enabled,
                "100",
                new MemberMessageAppearance(
                        "Welcome to {server}!", "Hi {member}", 0x57F287, null,
                        "Welcome • {server}", true, false, null, null),
                version);
    }
}
