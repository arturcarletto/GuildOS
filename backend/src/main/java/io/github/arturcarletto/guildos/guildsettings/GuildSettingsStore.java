package io.github.arturcarletto.guildos.guildsettings;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;
import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
class GuildSettingsStore {

    private final GuildSettingsRepository repository;
    private final GuildAccessAuthorizer authorizer;
    private final GuildAuditRecorder auditRecorder;
    private final Clock clock;

    GuildSettingsStore(
            GuildSettingsRepository repository,
            GuildAccessAuthorizer authorizer,
            GuildAuditRecorder auditRecorder,
            Clock clock) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    GuildSettingsResponse get(UUID operatorId, String discordGuildId) {
        Instant now = clock.instant();
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(GuildSettingsNotFoundException::new);
        repository.insertIfAbsent(UUID.randomUUID(), access.registeredGuildId(), now);
        GuildSettings settings = repository.findByRegisteredGuildId(access.registeredGuildId())
                .orElseThrow(() -> new IllegalStateException(
                        "Guild settings were not available after insert-if-absent"));
        return toResponse(access, settings);
    }

    @Transactional
    GuildSettingsResponse update(
            UUID operatorId,
            String discordGuildId,
            NormalizedGuildSettings replacement,
            long expectedVersion) {
        Instant now = clock.instant();
        AuthorizedGuildAccess access = authorizer.findActiveForUpdate(operatorId, discordGuildId)
                .orElseThrow(GuildSettingsNotFoundException::new);
        repository.insertIfAbsent(UUID.randomUUID(), access.registeredGuildId(), now);
        GuildSettings settings = repository.findByRegisteredGuildIdForUpdate(access.registeredGuildId())
                .orElseThrow(() -> new IllegalStateException(
                        "Guild settings were not available after insert-if-absent"));

        if (settings.getVersion() != expectedVersion) {
            throw new GuildSettingsConflictException();
        }
        if (settings.update(replacement, now)) {
            repository.flush();
            auditRecorder.recordOperatorEvent(
                    access.registeredGuildId(),
                    operatorId,
                    GuildAuditEventType.GUILD_SETTINGS_UPDATED);
        }
        return toResponse(access, settings);
    }

    private static GuildSettingsResponse toResponse(
            AuthorizedGuildAccess access, GuildSettings settings) {
        return new GuildSettingsResponse(
                access.discordGuildId(),
                access.name(),
                settings.getTimezone(),
                settings.getLocaleTag(),
                settings.getVersion(),
                settings.getUpdatedAt());
    }
}
