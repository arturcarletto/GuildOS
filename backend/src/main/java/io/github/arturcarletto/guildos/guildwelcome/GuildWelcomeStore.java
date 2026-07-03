package io.github.arturcarletto.guildos.guildwelcome;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    StoredGuildWelcome configure(
            UUID registeredGuildId, String channelId, String messageTemplate) {
        Instant now = clock.instant();
        int inserted = repository.insertIfAbsent(
                UUID.randomUUID(), registeredGuildId, channelId, messageTemplate, now);
        GuildWelcomeConfiguration configuration = repository
                .findByRegisteredGuildId(registeredGuildId)
                .orElseThrow(() -> new IllegalStateException(
                        "Welcome configuration was not available after insert-if-absent"));
        if (inserted == 0) {
            configuration.configure(channelId, messageTemplate, now);
        }
        // Flush before mapping so updates expose their new version. A managed no-op stays clean.
        repository.flush();
        return toStored(configuration);
    }

    @Transactional
    Optional<StoredGuildWelcome> disable(UUID registeredGuildId) {
        Instant now = clock.instant();
        Optional<GuildWelcomeConfiguration> existing =
                repository.findByRegisteredGuildId(registeredGuildId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        GuildWelcomeConfiguration configuration = existing.get();
        if (configuration.disable(now)) {
            repository.flush();
        }
        return Optional.of(toStored(configuration));
    }

    private static StoredGuildWelcome toStored(GuildWelcomeConfiguration configuration) {
        return new StoredGuildWelcome(
                configuration.isEnabled(),
                configuration.getChannelId(),
                configuration.getMessageTemplate(),
                configuration.getVersion());
    }
}
