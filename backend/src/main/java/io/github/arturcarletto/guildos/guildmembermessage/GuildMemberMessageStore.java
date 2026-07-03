package io.github.arturcarletto.guildos.guildmembermessage;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-managed persistence boundary for member-message configurations. Every mutation runs in
 * its own short transaction, operates against a caller-supplied state snapshot (expected-absent for
 * a create, expected-version for update/toggle), and contains no Discord work. JPA {@code @Version}
 * remains the final concurrent-write backstop behind these explicit snapshot checks. Rows for
 * different kinds of the same guild are independent because the unique key includes the kind.
 */
@Service
class GuildMemberMessageStore {

    private final GuildMemberMessageConfigurationRepository repository;
    private final Clock clock;

    GuildMemberMessageStore(GuildMemberMessageConfigurationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Optional<StoredGuildMemberMessage> find(UUID registeredGuildId, MemberMessageKind kind) {
        return repository.findByRegisteredGuildIdAndMessageKind(registeredGuildId, kind)
                .map(GuildMemberMessageStore::toStored);
    }

    @Transactional
    StoredGuildMemberMessage createIfAbsent(
            UUID registeredGuildId,
            MemberMessageKind kind,
            String channelId,
            MemberMessageAppearance appearance) {
        Instant now = clock.instant();
        int inserted = repository.insertIfAbsent(
                UUID.randomUUID(),
                registeredGuildId,
                kind.name(),
                channelId,
                appearance.title(),
                appearance.description(),
                appearance.accentColor(),
                appearance.imageUrl(),
                appearance.footer(),
                appearance.mentionMember(),
                appearance.includeBots(),
                appearance.buttonLabel(),
                appearance.buttonUrl(),
                now);
        if (inserted == 0) {
            throw new GuildMemberMessageConflictException();
        }
        return toStored(loadOrThrowConflict(registeredGuildId, kind));
    }

    @Transactional
    StoredGuildMemberMessage configureExisting(
            UUID registeredGuildId,
            MemberMessageKind kind,
            String channelId,
            MemberMessageAppearance appearance,
            long expectedVersion) {
        GuildMemberMessageConfiguration configuration =
                loadAtExpectedVersion(registeredGuildId, kind, expectedVersion);
        if (configuration.configure(channelId, appearance, clock.instant())) {
            repository.flush();
        }
        return toStored(configuration);
    }

    @Transactional
    StoredGuildMemberMessage toggleExisting(
            UUID registeredGuildId, MemberMessageKind kind, long expectedVersion) {
        GuildMemberMessageConfiguration configuration =
                loadAtExpectedVersion(registeredGuildId, kind, expectedVersion);
        configuration.toggle(clock.instant());
        repository.flush();
        return toStored(configuration);
    }

    private GuildMemberMessageConfiguration loadAtExpectedVersion(
            UUID registeredGuildId, MemberMessageKind kind, long expectedVersion) {
        GuildMemberMessageConfiguration configuration = loadOrThrowConflict(registeredGuildId, kind);
        if (configuration.getVersion() != expectedVersion) {
            throw new GuildMemberMessageConflictException();
        }
        return configuration;
    }

    private GuildMemberMessageConfiguration loadOrThrowConflict(
            UUID registeredGuildId, MemberMessageKind kind) {
        return repository.findByRegisteredGuildIdAndMessageKind(registeredGuildId, kind)
                .orElseThrow(GuildMemberMessageConflictException::new);
    }

    private static StoredGuildMemberMessage toStored(GuildMemberMessageConfiguration configuration) {
        return new StoredGuildMemberMessage(
                configuration.getMessageKind(),
                configuration.isEnabled(),
                configuration.getChannelId(),
                configuration.toAppearance(),
                configuration.getVersion());
    }
}
