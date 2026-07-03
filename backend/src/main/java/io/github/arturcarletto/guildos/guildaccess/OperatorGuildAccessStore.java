package io.github.arturcarletto.guildos.guildaccess;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the short transactional persistence of the operator-to-guild authorization.
 *
 * <p>It is a separate collaborator so the orchestrating {@link GuildOnboardingService} can perform
 * the outbound Discord call and eligibility checks with no transaction open, then invoke these
 * transactional methods through the Spring proxy (avoiding self-invocation). Each method captures
 * {@link Clock#instant()} exactly once and keeps the insert-if-absent plus pessimistic-lock flow
 * inside a single transaction.
 */
@Service
class OperatorGuildAccessStore {

    private final OperatorGuildAccessRepository repository;
    private final Clock clock;

    OperatorGuildAccessStore(OperatorGuildAccessRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    OnboardingOutcome onboard(UUID operatorId, UUID registeredGuildId, GuildAccessRole role) {
        Instant now = clock.instant();
        int inserted = repository.insertIfAbsent(
                UUID.randomUUID(), operatorId, registeredGuildId, role.name(), now);

        OperatorGuildAccess access = repository
                .findByOperatorIdAndRegisteredGuildIdForUpdate(operatorId, registeredGuildId)
                .orElseThrow(() -> new IllegalStateException(
                        "Authorization was not available after insert-if-absent"));
        return inserted == 1 ? OnboardingOutcome.CREATED : access.onboard(role, now);
    }

    @Transactional
    void revoke(UUID operatorId, UUID registeredGuildId) {
        Instant now = clock.instant();
        repository.findByOperatorIdAndRegisteredGuildIdForUpdate(operatorId, registeredGuildId)
                .ifPresent(access -> access.revoke(now));
    }
}
