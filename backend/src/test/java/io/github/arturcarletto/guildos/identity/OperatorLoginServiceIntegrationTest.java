package io.github.arturcarletto.guildos.identity;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.arturcarletto.guildos.FixedClockTestConfiguration;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
class OperatorLoginServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Autowired
    private OperatorLoginService service;

    @Autowired
    private OperatorAccountRepository repository;

    @BeforeEach
    void clearOperators() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void firstLoginPersistsAnOperatorAccount() {
        OperatorIdentity identity = service.login(command("operator-1", "operator", "Operator One"));

        OperatorAccount account = repository.findByDiscordUserId("operator-1").orElseThrow();
        assertThat(identity.operatorId()).isEqualTo(account.getId());
        assertThat(account.getUsername()).isEqualTo("operator");
        assertThat(account.getGlobalDisplayName()).isEqualTo("Operator One");
        assertThat(account.getAvatarHash()).isEqualTo("avatar-hash");
        assertThat(account.getFirstLoginAt()).isEqualTo(NOW);
        assertThat(account.getLastLoginAt()).isEqualTo(NOW);
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void repeatedLoginUpdatesProfileAndPreservesIdentityAndFirstLogin() {
        service.login(command("operator-2", "old-name", "Old Display"));
        OperatorAccount firstLogin = repository.findByDiscordUserId("operator-2").orElseThrow();
        UUID id = firstLogin.getId();
        Instant firstLoginAt = firstLogin.getFirstLoginAt();
        Instant createdAt = firstLogin.getCreatedAt();

        service.login(new OperatorLoginCommand("operator-2", "new-name", "New Display", "new-avatar"));

        OperatorAccount repeatedLogin = repository.findByDiscordUserId("operator-2").orElseThrow();
        assertThat(repeatedLogin.getId()).isEqualTo(id);
        assertThat(repeatedLogin.getFirstLoginAt()).isEqualTo(firstLoginAt);
        assertThat(repeatedLogin.getCreatedAt()).isEqualTo(createdAt);
        assertThat(repeatedLogin.getUsername()).isEqualTo("new-name");
        assertThat(repeatedLogin.getGlobalDisplayName()).isEqualTo("New Display");
        assertThat(repeatedLogin.getAvatarHash()).isEqualTo("new-avatar");
        assertThat(repeatedLogin.getLastLoginAt()).isEqualTo(NOW);
        assertThat(repeatedLogin.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void concurrentFirstLoginsBothSucceedWithoutCreatingDuplicates() throws Exception {
        OperatorLoginCommand command = command("operator-concurrent", "concurrent", "Concurrent Operator");
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<OperatorIdentity> first = executor.submit(() -> loginAfterSignal(command, callersReady, start));
            Future<OperatorIdentity> second = executor.submit(() -> loginAfterSignal(command, callersReady, start));
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            OperatorIdentity firstIdentity = first.get(10, TimeUnit.SECONDS);
            OperatorIdentity secondIdentity = second.get(10, TimeUnit.SECONDS);
            assertThat(secondIdentity.operatorId()).isEqualTo(firstIdentity.operatorId());
        }
        finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
    }

    private OperatorLoginCommand command(String id, String username, String displayName) {
        return new OperatorLoginCommand(id, username, displayName, "avatar-hash");
    }

    private OperatorIdentity loginAfterSignal(
            OperatorLoginCommand command,
            CountDownLatch callersReady,
            CountDownLatch start) throws InterruptedException {
        callersReady.countDown();
        start.await();
        return service.login(command);
    }
}
