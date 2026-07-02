package io.github.arturcarletto.guildos.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({TestcontainersConfiguration.class, MutableClockTestConfiguration.class})
class OperatorLoginServiceIntegrationTest {

    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(90, ChronoUnit.MINUTES);

    @Autowired
    private OperatorLoginService service;

    @Autowired
    private OperatorAccountRepository repository;

    @Autowired
    private MutableTestClock clock;

    @BeforeEach
    void clearOperators() {
        clock.setInstant(INSTANT_A);
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
        assertThat(account.getFirstLoginAt()).isEqualTo(INSTANT_A);
        assertThat(account.getLastLoginAt()).isEqualTo(INSTANT_A);
        assertThat(account.getCreatedAt()).isEqualTo(INSTANT_A);
        assertThat(account.getUpdatedAt()).isEqualTo(INSTANT_A);
    }

    @Test
    void repeatedLoginUpdatesProfileAndPreservesIdentityAndFirstLogin() {
        service.login(command("operator-2", "old-name", "Old Display"));
        OperatorAccount firstLogin = repository.findByDiscordUserId("operator-2").orElseThrow();
        UUID id = firstLogin.getId();
        assertThat(firstLogin.getFirstLoginAt()).isEqualTo(INSTANT_A);
        assertThat(firstLogin.getCreatedAt()).isEqualTo(INSTANT_A);
        assertThat(firstLogin.getLastLoginAt()).isEqualTo(INSTANT_A);
        assertThat(firstLogin.getUpdatedAt()).isEqualTo(INSTANT_A);

        clock.setInstant(INSTANT_B);
        service.login(new OperatorLoginCommand("operator-2", "new-name", "New Display", "new-avatar"));

        OperatorAccount repeatedLogin = repository.findByDiscordUserId("operator-2").orElseThrow();
        assertThat(repeatedLogin.getId()).isEqualTo(id);
        assertThat(repeatedLogin.getFirstLoginAt()).isEqualTo(INSTANT_A);
        assertThat(repeatedLogin.getCreatedAt()).isEqualTo(INSTANT_A);
        assertThat(repeatedLogin.getLastLoginAt()).isEqualTo(INSTANT_B);
        assertThat(repeatedLogin.getUpdatedAt()).isEqualTo(INSTANT_B);
        assertThat(repeatedLogin.getUsername()).isEqualTo("new-name");
        assertThat(repeatedLogin.getGlobalDisplayName()).isEqualTo("New Display");
        assertThat(repeatedLogin.getAvatarHash()).isEqualTo("new-avatar");
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
