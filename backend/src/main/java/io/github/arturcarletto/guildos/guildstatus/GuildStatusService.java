package io.github.arturcarletto.guildos.guildstatus;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsReader;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsView;

/** Platform-neutral, read-only status use case for a Discord guild. */
@Service
public class GuildStatusService {

    static final String DEFAULT_TIMEZONE = "UTC";
    static final String DEFAULT_LOCALE = "en-US";

    private final GuildDirectory guildDirectory;
    private final GuildOnboardingDirectory onboardingDirectory;
    private final GuildSettingsReader settingsReader;

    GuildStatusService(
            GuildDirectory guildDirectory,
            GuildOnboardingDirectory onboardingDirectory,
            GuildSettingsReader settingsReader) {
        this.guildDirectory = guildDirectory;
        this.onboardingDirectory = onboardingDirectory;
        this.settingsReader = settingsReader;
    }

    public GuildStatusView resolve(String discordGuildId) {
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(discordGuildId)
                .filter(RegisteredGuildView::connected)
                .orElse(null);
        if (guild == null) {
            return GuildStatusView.unavailable();
        }
        if (!onboardingDirectory.isOnboarded(discordGuildId)) {
            return GuildStatusView.notOnboarded(guild.name());
        }

        GuildSettingsView settings = settingsReader.find(discordGuildId)
                .orElse(new GuildSettingsView(DEFAULT_TIMEZONE, DEFAULT_LOCALE, 0));
        return GuildStatusView.active(
                guild.name(),
                settings.timezone(),
                settings.locale(),
                settings.version());
    }
}
