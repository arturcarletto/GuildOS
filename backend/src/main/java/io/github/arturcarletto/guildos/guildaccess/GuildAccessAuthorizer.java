package io.github.arturcarletto.guildos.guildaccess;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;

/**
 * Public authorization contract for capabilities that operate on a registered guild.
 *
 * <p>The authenticated operator id must come from the server-side principal. A missing guild and a
 * missing or revoked authorization deliberately produce the same empty result so callers cannot
 * use this contract to discover another operator's guilds.
 */
@Service
public class GuildAccessAuthorizer {

    private final OperatorGuildAccessRepository repository;
    private final GuildDirectory guildDirectory;

    GuildAccessAuthorizer(OperatorGuildAccessRepository repository, GuildDirectory guildDirectory) {
        this.repository = repository;
        this.guildDirectory = guildDirectory;
    }

    @Transactional(readOnly = true)
    public Optional<AuthorizedGuildAccess> findActive(UUID operatorId, String discordGuildId) {
        RegisteredGuildView guild = resolveGuild(discordGuildId).orElse(null);
        if (guild == null) {
            return Optional.empty();
        }
        return repository
                .findByOperatorIdAndRegisteredGuildId(operatorId, guild.registeredGuildId())
                .filter(OperatorGuildAccess::isActive)
                .map(access -> toAuthorizedAccess(guild, access));
    }

    /**
     * Rechecks active access while taking the same row lock used by revocation. The caller must
     * already own the short local transaction that will perform the protected mutation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AuthorizedGuildAccess> findActiveForUpdate(UUID operatorId, String discordGuildId) {
        RegisteredGuildView guild = resolveGuild(discordGuildId).orElse(null);
        if (guild == null) {
            return Optional.empty();
        }
        return repository
                .findByOperatorIdAndRegisteredGuildIdForUpdate(operatorId, guild.registeredGuildId())
                .filter(OperatorGuildAccess::isActive)
                .map(access -> toAuthorizedAccess(guild, access));
    }

    private Optional<RegisteredGuildView> resolveGuild(String discordGuildId) {
        return guildDirectory.findByDiscordGuildId(DiscordSnowflakes.requireValid(discordGuildId));
    }

    private static AuthorizedGuildAccess toAuthorizedAccess(
            RegisteredGuildView guild, OperatorGuildAccess access) {
        return new AuthorizedGuildAccess(
                guild.registeredGuildId(),
                guild.discordGuildId(),
                guild.name(),
                access.getRole().name(),
                guild.connected());
    }
}
