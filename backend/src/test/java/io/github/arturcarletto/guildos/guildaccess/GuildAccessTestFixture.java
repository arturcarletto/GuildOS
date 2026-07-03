package io.github.arturcarletto.guildos.guildaccess;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.springframework.transaction.annotation.Transactional;

/** Test-only bridge that seeds and coordinates the package-private authorization model. */
public class GuildAccessTestFixture {

    private final OperatorGuildAccessRepository repository;
    private final OperatorGuildAccessStore store;
    private final Clock clock;

    GuildAccessTestFixture(
            OperatorGuildAccessRepository repository,
            OperatorGuildAccessStore store,
            Clock clock) {
        this.repository = repository;
        this.store = store;
        this.clock = clock;
    }

    public void clear() {
        repository.deleteAll();
        repository.flush();
    }

    public void authorizeOwner(UUID operatorId, UUID registeredGuildId) {
        store.onboard(operatorId, registeredGuildId, GuildAccessRole.OWNER);
    }

    public void authorizeAdmin(UUID operatorId, UUID registeredGuildId) {
        store.onboard(operatorId, registeredGuildId, GuildAccessRole.ADMIN);
    }

    public void revoke(UUID operatorId, UUID registeredGuildId) {
        store.revoke(operatorId, registeredGuildId);
    }

    @Transactional
    public void revokeAndHold(
            UUID operatorId,
            UUID registeredGuildId,
            CountDownLatch lockAcquired,
            CountDownLatch releaseLock) {
        Instant now = clock.instant();
        OperatorGuildAccess access = repository
                .findByOperatorIdAndRegisteredGuildIdForUpdate(operatorId, registeredGuildId)
                .orElseThrow();
        access.revoke(now);
        repository.flush();
        lockAcquired.countDown();
        try {
            releaseLock.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding authorization test lock", exception);
        }
    }
}
