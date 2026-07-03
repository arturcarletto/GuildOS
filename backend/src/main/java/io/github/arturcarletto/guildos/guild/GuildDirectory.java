package io.github.arturcarletto.guildos.guild;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, application-facing contract over the guild registry for other capabilities.
 *
 * <p>It exposes only {@link RegisteredGuildView} projections so callers never touch the
 * package-private {@link Guild} entity or {@link GuildRepository} directly.
 */
@Service
public class GuildDirectory {

    private final GuildRepository repository;

    GuildDirectory(GuildRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<RegisteredGuildView> findByDiscordGuildId(String discordGuildId) {
        return repository.findByDiscordGuildId(discordGuildId).map(GuildDirectory::toView);
    }

    @Transactional(readOnly = true)
    public List<RegisteredGuildView> findAllByDiscordGuildIds(Collection<String> discordGuildIds) {
        if (discordGuildIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByDiscordGuildIdIn(discordGuildIds).stream()
                .map(GuildDirectory::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegisteredGuildView> findAllByRegisteredGuildIds(Collection<UUID> registeredGuildIds) {
        if (registeredGuildIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdIn(registeredGuildIds).stream()
                .map(GuildDirectory::toView)
                .toList();
    }

    private static RegisteredGuildView toView(Guild guild) {
        return new RegisteredGuildView(
                guild.getId(),
                guild.getDiscordGuildId(),
                guild.getName(),
                guild.getConnectionStatus() == GuildConnectionStatus.CONNECTED);
    }
}
