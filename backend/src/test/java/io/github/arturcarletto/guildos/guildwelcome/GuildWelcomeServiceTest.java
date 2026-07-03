package io.github.arturcarletto.guildos.guildwelcome;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildWelcomeServiceTest {

    private static final String DISCORD_GUILD_ID = "700000000000000001";
    private static final UUID REGISTERED_GUILD_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private final GuildDirectory guildDirectory = mock(GuildDirectory.class);
    private final GuildOnboardingDirectory onboardingDirectory =
            mock(GuildOnboardingDirectory.class);
    private final GuildWelcomeStore store = mock(GuildWelcomeStore.class);
    private final GuildWelcomeService service =
            new GuildWelcomeService(guildDirectory, onboardingDirectory, store);

    @Test
    void unknownAndDisconnectedGuildsAreUnavailable() {
        when(guildDirectory.findByDiscordGuildId(DISCORD_GUILD_ID)).thenReturn(Optional.empty());
        assertThat(service.status(DISCORD_GUILD_ID).state())
                .isEqualTo(GuildWelcomeState.UNAVAILABLE);

        when(guildDirectory.findByDiscordGuildId(DISCORD_GUILD_ID))
                .thenReturn(Optional.of(guild(false)));
        assertThat(service.status(DISCORD_GUILD_ID).state())
                .isEqualTo(GuildWelcomeState.UNAVAILABLE);

        verify(onboardingDirectory, never()).isOnboarded(anyString());
        verify(store, never()).find(any());
    }

    @Test
    void connectedNonOnboardedGuildRequiresOnboarding() {
        when(guildDirectory.findByDiscordGuildId(DISCORD_GUILD_ID))
                .thenReturn(Optional.of(guild(true)));
        when(onboardingDirectory.isOnboarded(DISCORD_GUILD_ID)).thenReturn(false);

        GuildWelcomeView result = service.status(DISCORD_GUILD_ID);

        assertThat(result.state()).isEqualTo(GuildWelcomeState.ONBOARDING_REQUIRED);
        assertThat(result.guildName()).isEqualTo("Heaven");
        verify(store, never()).find(any());
    }

    @Test
    void onboardedGuildWithoutConfigurationIsNotConfiguredAndReadOnly() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID)).thenReturn(Optional.empty());

        assertThat(service.status(DISCORD_GUILD_ID).state())
                .isEqualTo(GuildWelcomeState.NOT_CONFIGURED);
        assertThat(service.preview(
                        DISCORD_GUILD_ID,
                        new WelcomePreviewContext("Artur", "Heaven", 42))
                .state()).isEqualTo(GuildWelcomeState.NOT_CONFIGURED);

        verify(store, never()).createIfAbsent(any(), anyString(), anyString());
        verify(store, never()).configureExisting(any(), anyString(), anyString(), anyLong());
        verify(store, never()).disableExisting(any(), anyLong());
    }

    @Test
    void configureFromAbsentSnapshotCreatesAndReturnsSafePersistedState() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID)).thenReturn(Optional.empty());
        when(store.createIfAbsent(REGISTERED_GUILD_ID, "8001", "Welcome {member}"))
                .thenReturn(new StoredGuildWelcome(true, "8001", "Welcome {member}", 0));

        GuildWelcomeView result =
                service.configure(DISCORD_GUILD_ID, "8001", " \r\nWelcome {member}\r\n ");

        assertThat(result.state()).isEqualTo(GuildWelcomeState.CONFIGURED);
        assertThat(result.enabled()).isTrue();
        assertThat(result.channelId()).isEqualTo("8001");
        assertThat(result.messageTemplate()).isEqualTo("Welcome {member}");
        assertThat(result.version()).isZero();
        assertThat(result.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("id", "registeredGuildId", "operatorId");
        verify(store, never()).configureExisting(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void configureFromPresentSnapshotUpdatesAgainstTheObservedVersion() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID))
                .thenReturn(Optional.of(new StoredGuildWelcome(true, "8000", "Old", 4)));
        when(store.configureExisting(REGISTERED_GUILD_ID, "8001", "Welcome {member}", 4))
                .thenReturn(new StoredGuildWelcome(true, "8001", "Welcome {member}", 5));

        GuildWelcomeView result =
                service.configure(DISCORD_GUILD_ID, "8001", "Welcome {member}");

        assertThat(result.version()).isEqualTo(5);
        assertThat(result.channelId()).isEqualTo("8001");
        verify(store).configureExisting(REGISTERED_GUILD_ID, "8001", "Welcome {member}", 4);
        verify(store, never()).createIfAbsent(any(), anyString(), anyString());
    }

    @Test
    void configureLostCreateRaceBecomesAControlledConflict() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID)).thenReturn(Optional.empty());
        when(store.createIfAbsent(REGISTERED_GUILD_ID, "8001", "Welcome"))
                .thenThrow(new GuildWelcomeConflictException());

        assertThatThrownBy(() -> service.configure(DISCORD_GUILD_ID, "8001", "Welcome"))
                .isInstanceOf(GuildWelcomeConflictException.class);
    }

    @Test
    void invalidTemplateIsRejectedBeforePersistence() {
        allowAccess();

        assertThatThrownBy(() -> service.configure(
                        DISCORD_GUILD_ID, "8001", "Hello @everyone"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);

        verify(store, never()).find(any());
        verify(store, never()).createIfAbsent(any(), anyString(), anyString());
        verify(store, never()).configureExisting(any(), anyString(), anyString(), anyLong());
    }

    @Test
    void previewRendersConfiguredStateWithoutWriting() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID)).thenReturn(Optional.of(
                new StoredGuildWelcome(false, "8001", "Hi {member} at {server} #{memberCount}", 3)));

        GuildWelcomeView result = service.preview(
                DISCORD_GUILD_ID,
                new WelcomePreviewContext("Artur", "Heaven", 42));

        assertThat(result.enabled()).isFalse();
        assertThat(result.renderedPreview()).isEqualTo("Hi Artur at Heaven #42");
        verify(store, never()).createIfAbsent(any(), anyString(), anyString());
        verify(store, never()).configureExisting(any(), anyString(), anyString(), anyLong());
        verify(store, never()).disableExisting(any(), anyLong());
    }

    @Test
    void disableReturnsNotConfiguredWithoutMaterializingARow() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID)).thenReturn(Optional.empty());

        assertThat(service.disable(DISCORD_GUILD_ID).state())
                .isEqualTo(GuildWelcomeState.NOT_CONFIGURED);
        verify(store, never()).disableExisting(any(), anyLong());
    }

    @Test
    void disableFromPresentSnapshotDisablesAgainstTheObservedVersion() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID))
                .thenReturn(Optional.of(new StoredGuildWelcome(true, "8001", "Welcome", 6)));
        when(store.disableExisting(REGISTERED_GUILD_ID, 6))
                .thenReturn(new StoredGuildWelcome(false, "8001", "Welcome", 7));

        GuildWelcomeView result = service.disable(DISCORD_GUILD_ID);

        assertThat(result.enabled()).isFalse();
        assertThat(result.version()).isEqualTo(7);
        verify(store).disableExisting(REGISTERED_GUILD_ID, 6);
    }

    @Test
    void optimisticPersistenceFailureBecomesAControlledWelcomeConflict() {
        allowAccess();
        when(store.find(REGISTERED_GUILD_ID))
                .thenReturn(Optional.of(new StoredGuildWelcome(true, "8000", "Old", 1)));
        when(store.configureExisting(REGISTERED_GUILD_ID, "8001", "Welcome", 1))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        GuildWelcomeConfiguration.class, REGISTERED_GUILD_ID));

        assertThatThrownBy(() -> service.configure(DISCORD_GUILD_ID, "8001", "Welcome"))
                .isInstanceOf(GuildWelcomeConflictException.class)
                .hasMessage("The welcome configuration changed concurrently");
    }

    private void allowAccess() {
        when(guildDirectory.findByDiscordGuildId(DISCORD_GUILD_ID))
                .thenReturn(Optional.of(guild(true)));
        when(onboardingDirectory.isOnboarded(DISCORD_GUILD_ID)).thenReturn(true);
    }

    private RegisteredGuildView guild(boolean connected) {
        return new RegisteredGuildView(
                REGISTERED_GUILD_ID,
                DISCORD_GUILD_ID,
                "Heaven",
                connected);
    }
}
