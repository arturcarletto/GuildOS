package io.github.arturcarletto.guildos.guildwelcome;

import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;

/** Platform-neutral welcome configuration and preview use cases. */
@Service
public class GuildWelcomeService {

    private final GuildDirectory guildDirectory;
    private final GuildOnboardingDirectory onboardingDirectory;
    private final GuildWelcomeStore store;

    GuildWelcomeService(
            GuildDirectory guildDirectory,
            GuildOnboardingDirectory onboardingDirectory,
            GuildWelcomeStore store) {
        this.guildDirectory = guildDirectory;
        this.onboardingDirectory = onboardingDirectory;
        this.store = store;
    }

    public GuildWelcomeView status(String discordGuildId) {
        Access access = resolveAccess(discordGuildId);
        if (access.result() != null) {
            return access.result();
        }
        return store.find(access.registeredGuildId())
                .map(stored -> GuildWelcomeView.configured(access.guildName(), stored))
                .orElseGet(() -> GuildWelcomeView.notConfigured(access.guildName()));
    }

    public GuildWelcomeView configure(
            String discordGuildId, String channelId, String rawTemplate) {
        Access access = resolveAccess(discordGuildId);
        if (access.result() != null) {
            return access.result();
        }
        WelcomeTemplate template = WelcomeTemplate.parse(rawTemplate);
        try {
            StoredGuildWelcome stored =
                    store.configure(access.registeredGuildId(), channelId, template.value());
            return GuildWelcomeView.configured(access.guildName(), stored);
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildWelcomeConflictException();
        }
    }

    public GuildWelcomeView preview(
            String discordGuildId, WelcomePreviewContext context) {
        Access access = resolveAccess(discordGuildId);
        if (access.result() != null) {
            return access.result();
        }
        return store.find(access.registeredGuildId())
                .map(stored -> GuildWelcomeView.configured(
                        access.guildName(),
                        stored,
                        WelcomeTemplateRenderer.render(stored.messageTemplate(), context)))
                .orElseGet(() -> GuildWelcomeView.notConfigured(access.guildName()));
    }

    public GuildWelcomeView disable(String discordGuildId) {
        Access access = resolveAccess(discordGuildId);
        if (access.result() != null) {
            return access.result();
        }
        try {
            return store.disable(access.registeredGuildId())
                    .map(stored -> GuildWelcomeView.configured(access.guildName(), stored))
                    .orElseGet(() -> GuildWelcomeView.notConfigured(access.guildName()));
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildWelcomeConflictException();
        }
    }

    private Access resolveAccess(String discordGuildId) {
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(discordGuildId)
                .filter(RegisteredGuildView::connected)
                .orElse(null);
        if (guild == null) {
            return new Access(null, null, GuildWelcomeView.unavailable());
        }
        if (!onboardingDirectory.isOnboarded(discordGuildId)) {
            return new Access(
                    null,
                    null,
                    GuildWelcomeView.onboardingRequired(guild.name()));
        }
        return new Access(guild.registeredGuildId(), guild.name(), null);
    }

    private record Access(
            UUID registeredGuildId, String guildName, GuildWelcomeView result) {
    }
}
