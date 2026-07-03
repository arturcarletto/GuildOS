package io.github.arturcarletto.guildos.guildstatus;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsReader;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuildStatusServiceTest {

    private static final String GUILD_ID = "6001";

    private final GuildDirectory guildDirectory = mock(GuildDirectory.class);
    private final GuildOnboardingDirectory onboardingDirectory = mock(GuildOnboardingDirectory.class);
    private final GuildSettingsReader settingsReader = mock(GuildSettingsReader.class);
    private final GuildStatusService service =
            new GuildStatusService(guildDirectory, onboardingDirectory, settingsReader);

    @Test
    void connectedOnboardedGuildUsesPersistedSettings() {
        connectedGuild();
        when(onboardingDirectory.isOnboarded(GUILD_ID)).thenReturn(true);
        when(settingsReader.find(GUILD_ID)).thenReturn(Optional.of(
                new GuildSettingsView("America/Sao_Paulo", "pt-BR", 3)));

        GuildStatusView status = service.resolve(GUILD_ID);

        assertThat(status).isEqualTo(GuildStatusView.active(
                "Guild Six", "America/Sao_Paulo", "pt-BR", 3));
    }

    @Test
    void connectedOnboardedGuildUsesDefaultsWithoutPersistedSettings() {
        connectedGuild();
        when(onboardingDirectory.isOnboarded(GUILD_ID)).thenReturn(true);
        when(settingsReader.find(GUILD_ID)).thenReturn(Optional.empty());

        GuildStatusView status = service.resolve(GUILD_ID);

        assertThat(status).isEqualTo(GuildStatusView.active("Guild Six", "UTC", "en-US", 0));
    }

    @Test
    void connectedGuildWithoutActiveOnboardingDoesNotReadSettings() {
        connectedGuild();
        when(onboardingDirectory.isOnboarded(GUILD_ID)).thenReturn(false);

        GuildStatusView status = service.resolve(GUILD_ID);

        assertThat(status).isEqualTo(GuildStatusView.notOnboarded("Guild Six"));
        verifyNoInteractions(settingsReader);
    }

    @Test
    void unknownGuildIsUnavailableWithoutDownstreamReads() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.empty());

        assertThat(service.resolve(GUILD_ID)).isEqualTo(GuildStatusView.unavailable());
        verifyNoInteractions(onboardingDirectory, settingsReader);
    }

    @Test
    void disconnectedGuildIsUnavailableWithoutDownstreamReads() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.of(
                new RegisteredGuildView(UUID.randomUUID(), GUILD_ID, "Guild Six", false)));

        assertThat(service.resolve(GUILD_ID)).isEqualTo(GuildStatusView.unavailable());
        verifyNoInteractions(onboardingDirectory, settingsReader);
    }

    private void connectedGuild() {
        when(guildDirectory.findByDiscordGuildId(GUILD_ID)).thenReturn(Optional.of(
                new RegisteredGuildView(UUID.randomUUID(), GUILD_ID, "Guild Six", true)));
    }
}
