package io.github.arturcarletto.guildos.guildwelcome;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-managed persistence boundary for welcome configurations. Every mutation runs in its own
 * short transaction, operates against a caller-supplied state snapshot (expected-absent for a
 * create, expected-version for an update/disable), and contains no Discord work. JPA {@code
 * @Version} remains the final concurrent-write backstop behind these explicit snapshot checks.
 */
@Service
class GuildWelcomeStore {

    private final GuildWelcomeConfigurationRepository repository;
    private final Clock clock;

    GuildWelcomeStore(GuildWelcomeConfigurationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Optional<StoredGuildWelcome> find(UUID registeredGuildId) {
        return repository.findByRegisteredGuildId(registeredGuildId)
                .map(GuildWelcomeStore::toStored);
    }

    /**
     * Creates the first configuration for a guild that was absent in the caller's snapshot. If a
     * concurrent request created the row first, the conditional insert affects no rows and this
     * reports a controlled conflict instead of loading and overwriting that row.
     */
    @Transactional
    StoredGuildWelcome createIfAbsent(
            UUID registeredGuildId, String channelId, String messageTemplate) {
        Instant now = clock.instant();
        int inserted = repository.insertIfAbsent(
                UUID.randomUUID(), registeredGuildId, channelId, messageTemplate, now);
        if (inserted == 0) {
            throw new GuildWelcomeConflictException();
        }
        GuildWelcomeConfiguration configuration = repository
                .findByRegisteredGuildId(registeredGuildId)
                .orElseThrow(() -> new IllegalStateException(
                        "Welcome configuration was not available after a successful insert"));
        return toStored(configuration);
    }

    /**
     * Applies a configure request to a row the caller observed at {@code expectedVersion}. A row
     * that is now missing or at a different version is a controlled conflict; an identical enabled
     * configuration is a no-op that touches neither timestamp nor version.
     */
    @Transactional
    StoredGuildWelcome configureExisting(
            UUID registeredGuildId,
            String channelId,
            String messageTemplate,
            long expectedVersion) {
        GuildWelcomeConfiguration configuration =
                loadAtExpectedVersion(registeredGuildId, expectedVersion);
        if (configuration.configure(channelId, messageTemplate, clock.instant())) {
            repository.flush();
        }
        return toStored(configuration);
    }

    /**
     * Disables a row the caller observed at {@code expectedVersion}. A missing or changed row is a
     * controlled conflict; an already-disabled current row is a no-op that touches nothing.
     */
    @Transactional
    StoredGuildWelcome disableExisting(UUID registeredGuildId, long expectedVersion) {
        GuildWelcomeConfiguration configuration =
                loadAtExpectedVersion(registeredGuildId, expectedVersion);
        if (configuration.disable(clock.instant())) {
            repository.flush();
        }
        return toStored(configuration);
    }

    private GuildWelcomeConfiguration loadAtExpectedVersion(
            UUID registeredGuildId, long expectedVersion) {
        GuildWelcomeConfiguration configuration = repository
                .findByRegisteredGuildId(registeredGuildId)
                .orElseThrow(GuildWelcomeConflictException::new);
        if (configuration.getVersion() != expectedVersion) {
            throw new GuildWelcomeConflictException();
        }
        return configuration;
    }

    private static StoredGuildWelcome toStored(GuildWelcomeConfiguration configuration) {
        return new StoredGuildWelcome(
                configuration.isEnabled(),
                configuration.getChannelId(),
                configuration.getMessageTemplate(),
                configuration.getVersion());
    }
}
