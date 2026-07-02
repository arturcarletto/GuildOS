package io.github.arturcarletto.guildos.guild;

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
        "guildos.discord.token="
})
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
class GuildConnectionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Autowired
    private GuildConnectionService service;

    @Autowired
    private GuildRepository repository;

    @BeforeEach
    void clearGuilds() {
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void connectsANewGuild() {
        service.connect(new ConnectGuildCommand("1001", "Guild One"));

        Guild guild = repository.findByDiscordGuildId("1001").orElseThrow();
        assertThat(guild.getId()).isNotNull();
        assertThat(guild.getName()).isEqualTo("Guild One");
        assertThat(guild.getConnectionStatus()).isEqualTo(GuildConnectionStatus.CONNECTED);
        assertThat(guild.getFirstConnectedAt()).isEqualTo(NOW);
        assertThat(guild.getLastConnectedAt()).isEqualTo(NOW);
        assertThat(guild.getDisconnectedAt()).isNull();
        assertThat(guild.getCreatedAt()).isEqualTo(NOW);
        assertThat(guild.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void synchronizingTheSameGuildTwiceDoesNotCreateADuplicate() {
        ConnectGuildCommand command = new ConnectGuildCommand("1002", "Guild Two");

        service.connect(command);
        service.connect(command);

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void concurrentConnectionsForANewGuildBothSucceedWithoutCreatingDuplicates() throws Exception {
        ConnectGuildCommand command = new ConnectGuildCommand("concurrent-1002", "Concurrent Guild");
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> connectAfterSignal(command, callersReady, start));
            Future<?> second = executor.submit(() -> connectAfterSignal(command, callersReady, start));
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
        Guild guild = repository.findByDiscordGuildId("concurrent-1002").orElseThrow();
        assertThat(guild.getConnectionStatus()).isEqualTo(GuildConnectionStatus.CONNECTED);
    }

    @Test
    void synchronizationUpdatesMutableGuildMetadata() {
        service.connect(new ConnectGuildCommand("1003", "Old Name"));

        service.connect(new ConnectGuildCommand("1003", "New Name"));

        Guild guild = repository.findByDiscordGuildId("1003").orElseThrow();
        assertThat(guild.getName()).isEqualTo("New Name");
    }

    @Test
    void disconnectMarksTheGuildWithoutDeletingIt() {
        service.connect(new ConnectGuildCommand("1004", "Guild Four"));

        service.disconnect(new DisconnectGuildCommand("1004"));

        Guild guild = repository.findByDiscordGuildId("1004").orElseThrow();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(guild.getConnectionStatus()).isEqualTo(GuildConnectionStatus.DISCONNECTED);
        assertThat(guild.getDisconnectedAt()).isEqualTo(NOW);
        assertThat(guild.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void reconnectPreservesIdentityAndFirstConnectionWhileClearingDisconnection() {
        service.connect(new ConnectGuildCommand("1005", "Guild Five"));
        Guild initiallyConnected = repository.findByDiscordGuildId("1005").orElseThrow();
        UUID id = initiallyConnected.getId();
        Instant firstConnectedAt = initiallyConnected.getFirstConnectedAt();
        service.disconnect(new DisconnectGuildCommand("1005"));

        service.connect(new ConnectGuildCommand("1005", "Guild Five Reconnected"));

        Guild reconnected = repository.findByDiscordGuildId("1005").orElseThrow();
        assertThat(reconnected.getId()).isEqualTo(id);
        assertThat(reconnected.getFirstConnectedAt()).isEqualTo(firstConnectedAt);
        assertThat(reconnected.getName()).isEqualTo("Guild Five Reconnected");
        assertThat(reconnected.getConnectionStatus()).isEqualTo(GuildConnectionStatus.CONNECTED);
        assertThat(reconnected.getDisconnectedAt()).isNull();
        assertThat(reconnected.getLastConnectedAt()).isEqualTo(NOW);
    }

    @Test
    void repeatedDisconnectIsAnIdempotentNoOp() {
        service.connect(new ConnectGuildCommand("1006", "Guild Six"));
        service.disconnect(new DisconnectGuildCommand("1006"));
        Guild firstDisconnect = repository.findByDiscordGuildId("1006").orElseThrow();
        long version = firstDisconnect.getVersion();
        Instant disconnectedAt = firstDisconnect.getDisconnectedAt();

        service.disconnect(new DisconnectGuildCommand("1006"));

        Guild repeatedDisconnect = repository.findByDiscordGuildId("1006").orElseThrow();
        assertThat(repeatedDisconnect.getVersion()).isEqualTo(version);
        assertThat(repeatedDisconnect.getDisconnectedAt()).isEqualTo(disconnectedAt);
    }

    @Test
    void disconnectingAnUnknownGuildIsAnIdempotentNoOp() {
        service.disconnect(new DisconnectGuildCommand("unknown-guild"));

        assertThat(repository.count()).isZero();
    }

    private Void connectAfterSignal(
            ConnectGuildCommand command,
            CountDownLatch callersReady,
            CountDownLatch start) throws InterruptedException {
        callersReady.countDown();
        start.await();
        service.connect(command);
        return null;
    }
}
