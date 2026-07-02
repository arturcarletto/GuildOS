package io.github.arturcarletto.guildos.discord;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

final class DiscordGateway implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGateway.class);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final DiscordProperties properties;
    private final DiscordJdaFactory jdaFactory;
    private final Duration startupTimeout;
    private final Object lifecycleMonitor = new Object();

    private volatile JDA jda;
    private volatile LifecycleState lifecycleState = LifecycleState.STOPPED;

    DiscordGateway(DiscordProperties properties, DiscordJdaFactory jdaFactory) {
        this(properties, jdaFactory, STARTUP_TIMEOUT);
    }

    DiscordGateway(DiscordProperties properties, DiscordJdaFactory jdaFactory, Duration startupTimeout) {
        this.properties = properties;
        this.jdaFactory = jdaFactory;
        this.startupTimeout = requirePositive(startupTimeout);
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (lifecycleState != LifecycleState.STOPPED) {
                return;
            }
            lifecycleState = LifecycleState.STARTING;
        }

        JDA candidate = null;
        try {
            candidate = jdaFactory.connect(properties.getToken());
            if (!storeCandidate(candidate)) {
                try {
                    shutdownImmediately(candidate);
                }
                finally {
                    completeCancelledStartup();
                }
                return;
            }

            awaitReady(candidate);
            SelfUser botUser = candidate.getSelfUser();
            int guildCount = candidate.getGuilds().size();
            if (!markRunning(candidate)) {
                return;
            }
            logger.info("Discord Gateway connected: botUserId={}, botUsername={}, guildCount={}",
                    botUser.getId(), botUser.getName(), guildCount);
        }
        catch (TimeoutException exception) {
            abortStartup(candidate, true);
            throw new IllegalStateException("Discord Gateway startup timed out before becoming ready", exception);
        }
        catch (InterruptedException exception) {
            abortStartup(candidate, true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Discord Gateway to become ready",
                    exception);
        }
        catch (RuntimeException exception) {
            abortStartup(candidate, false);
            throw new IllegalStateException("Failed to connect to the Discord Gateway", exception);
        }
    }

    @Override
    public void stop() {
        JDA current;
        synchronized (lifecycleMonitor) {
            if (lifecycleState == LifecycleState.STOPPING) {
                return;
            }

            current = jda;
            jda = null;
            if (current == null) {
                lifecycleState = lifecycleState == LifecycleState.STARTING
                        ? LifecycleState.STOPPING
                        : LifecycleState.STOPPED;
                return;
            }
            lifecycleState = LifecycleState.STOPPING;
        }

        try {
            current.shutdown();
            try {
                if (!current.awaitShutdown(SHUTDOWN_TIMEOUT)) {
                    logger.warn("Discord Gateway did not shut down within {}; forcing shutdown", SHUTDOWN_TIMEOUT);
                    current.shutdownNow();
                }
            }
            catch (InterruptedException exception) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for the Discord Gateway to shut down; forced shutdown");
            }
        }
        finally {
            synchronized (lifecycleMonitor) {
                if (lifecycleState == LifecycleState.STOPPING) {
                    lifecycleState = LifecycleState.STOPPED;
                }
            }
        }
    }

    @Override
    public boolean isRunning() {
        return lifecycleState == LifecycleState.RUNNING;
    }

    DiscordGatewayState state() {
        JDA current = jda;
        if (current == null) {
            return new DiscordGatewayState(false, "NOT_INITIALIZED", 0);
        }

        JDA.Status status = current.getStatus();
        return new DiscordGatewayState(
                status == JDA.Status.CONNECTED,
                status.name(),
                current.getGuilds().size());
    }

    private void shutdownImmediately(JDA candidate) {
        if (candidate != null) {
            candidate.shutdownNow();
        }
    }

    private boolean storeCandidate(JDA candidate) {
        synchronized (lifecycleMonitor) {
            if (lifecycleState != LifecycleState.STARTING) {
                return false;
            }
            jda = candidate;
            return true;
        }
    }

    private boolean markRunning(JDA candidate) {
        synchronized (lifecycleMonitor) {
            if (lifecycleState != LifecycleState.STARTING || jda != candidate) {
                return false;
            }
            lifecycleState = LifecycleState.RUNNING;
            return true;
        }
    }

    private void abortStartup(JDA candidate, boolean terminateRegardless) {
        boolean ownsCandidate;
        boolean cancelledBeforeCandidate;
        synchronized (lifecycleMonitor) {
            ownsCandidate = lifecycleState == LifecycleState.STARTING
                    && (jda == candidate || candidate == null && jda == null);
            cancelledBeforeCandidate = !ownsCandidate
                    && lifecycleState == LifecycleState.STOPPING
                    && candidate == null
                    && jda == null;
            if (ownsCandidate) {
                jda = null;
                lifecycleState = LifecycleState.STOPPING;
            }
        }

        try {
            if (ownsCandidate || terminateRegardless) {
                shutdownImmediately(candidate);
            }
        }
        finally {
            if (ownsCandidate || cancelledBeforeCandidate) {
                completeCancelledStartup();
            }
        }
    }

    private void completeCancelledStartup() {
        synchronized (lifecycleMonitor) {
            if (lifecycleState == LifecycleState.STOPPING && jda == null) {
                lifecycleState = LifecycleState.STOPPED;
            }
        }
    }

    private void awaitReady(JDA candidate) throws InterruptedException, TimeoutException {
        FutureTask<JDA> readiness = new FutureTask<>(candidate::awaitReady);
        Thread.ofVirtual()
                .name("discord-gateway-readiness")
                .start(readiness);

        try {
            readiness.get(startupTimeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        catch (InterruptedException | TimeoutException exception) {
            readiness.cancel(true);
            throw exception;
        }
        catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InterruptedException interruptedException) {
                throw interruptedException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Discord Gateway readiness wait failed", cause);
        }
    }

    private static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "startupTimeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("startupTimeout must be positive");
        }
        return timeout;
    }

    private enum LifecycleState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }

    record DiscordGatewayState(boolean operational, String connectionStatus, int guildCount) {
    }
}
