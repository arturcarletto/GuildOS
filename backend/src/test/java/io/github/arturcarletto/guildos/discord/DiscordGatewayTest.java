package io.github.arturcarletto.guildos.discord;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordGatewayTest {

    @AfterEach
    void clearThreadInterruptionStatus() {
        Thread.interrupted();
    }

    @Test
    void startWaitsForReadyAndStopShutsDownGracefully() throws Exception {
        JDA jda = readyJda();
        when(jda.awaitShutdown(Duration.ofSeconds(10))).thenReturn(true);
        DiscordJdaFactory factory = mock(DiscordJdaFactory.class);
        when(factory.connect("test-token")).thenReturn(jda);
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                factory);

        gateway.start();

        assertThat(gateway.isRunning()).isTrue();
        verify(factory).connect("test-token");
        verify(jda).awaitReady();

        gateway.stop();

        assertThat(gateway.isRunning()).isFalse();
        verify(jda).shutdown();
        verify(jda).awaitShutdown(Duration.ofSeconds(10));
    }

    @Test
    void interruptedStartupRestoresTheInterruptionStatusAndStopsJda() throws Exception {
        JDA jda = mock(JDA.class);
        when(jda.awaitReady()).thenThrow(new InterruptedException("test interruption"));
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                token -> jda);

        assertThatThrownBy(gateway::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Interrupted while waiting for the Discord Gateway to become ready")
                .hasCauseInstanceOf(InterruptedException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(gateway.isRunning()).isFalse();
        verify(jda).shutdownNow();
    }

    @Test
    void startupTimeoutStopsAndClearsTheCandidate() throws Exception {
        JDA jda = mock(JDA.class);
        CountDownLatch neverReady = new CountDownLatch(1);
        when(jda.awaitReady()).thenAnswer(invocation -> {
            neverReady.await();
            return jda;
        });
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                token -> jda,
                Duration.ofMillis(100));

        assertThatThrownBy(gateway::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Discord Gateway startup timed out before becoming ready")
                .hasCauseInstanceOf(TimeoutException.class);

        assertThat(gateway.isRunning()).isFalse();
        assertThat(gateway.state().connectionStatus()).isEqualTo("NOT_INITIALIZED");
        verify(jda).shutdownNow();
    }

    @Test
    void stopIsSafeWhileStartupIsWaitingAndConcurrentStartDoesNotCreateAnotherClient() throws Exception {
        JDA jda = readyJda();
        CountDownLatch readinessStarted = new CountDownLatch(1);
        CountDownLatch finishReadiness = new CountDownLatch(1);
        when(jda.awaitReady()).thenAnswer(invocation -> {
            readinessStarted.countDown();
            finishReadiness.await();
            return jda;
        });
        doAnswer(invocation -> {
            finishReadiness.countDown();
            return null;
        }).when(jda).shutdown();
        when(jda.awaitShutdown(Duration.ofSeconds(10))).thenReturn(true);
        DiscordJdaFactory factory = mock(DiscordJdaFactory.class);
        when(factory.connect("test-token")).thenReturn(jda);
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                factory);
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        Thread startupThread = Thread.ofPlatform().start(() -> {
            try {
                gateway.start();
            }
            catch (Throwable throwable) {
                startupFailure.set(throwable);
            }
        });

        try {
            assertThat(readinessStarted.await(2, TimeUnit.SECONDS)).isTrue();

            gateway.start();
            gateway.stop();
        }
        finally {
            finishReadiness.countDown();
            startupThread.join(2_000);
        }

        assertThat(startupThread.isAlive()).isFalse();
        assertThat(startupFailure.get()).isNull();
        assertThat(gateway.isRunning()).isFalse();
        verify(factory, times(1)).connect("test-token");
        verify(jda).shutdown();
        verify(jda).awaitShutdown(Duration.ofSeconds(10));
    }

    @Test
    void stopForcesShutdownWhenTheGracefulTimeoutExpires() throws Exception {
        JDA jda = readyJda();
        when(jda.awaitShutdown(Duration.ofSeconds(10))).thenReturn(false);
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                token -> jda);
        gateway.start();

        gateway.stop();

        verify(jda).shutdown();
        verify(jda).shutdownNow();
    }

    @Test
    void connectionFailureProducesAClearStartupException() {
        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                token -> {
                    throw new IllegalArgumentException("simulated connection failure");
                });

        assertThatThrownBy(gateway::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to connect to the Discord Gateway")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private JDA readyJda() throws InterruptedException {
        JDA jda = mock(JDA.class);
        SelfUser selfUser = mock(SelfUser.class);
        when(jda.awaitReady()).thenReturn(jda);
        when(jda.getSelfUser()).thenReturn(selfUser);
        when(selfUser.getId()).thenReturn("123456789");
        when(selfUser.getName()).thenReturn("guild-os-test");
        return jda;
    }
}
