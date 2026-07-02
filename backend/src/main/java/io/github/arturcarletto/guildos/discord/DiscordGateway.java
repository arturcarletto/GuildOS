package io.github.arturcarletto.guildos.discord;

import java.time.Duration;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.SelfUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

final class DiscordGateway implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGateway.class);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final DiscordProperties properties;
    private final DiscordJdaFactory jdaFactory;

    private volatile JDA jda;
    private volatile boolean running;

    DiscordGateway(DiscordProperties properties, DiscordJdaFactory jdaFactory) {
        this.properties = properties;
        this.jdaFactory = jdaFactory;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        JDA candidate = null;
        try {
            candidate = jdaFactory.connect(properties.getToken());
            candidate.awaitReady();
            SelfUser botUser = candidate.getSelfUser();
            int guildCount = candidate.getGuilds().size();
            jda = candidate;
            running = true;
            logger.info("Discord Gateway connected: botUserId={}, botUsername={}, guildCount={}",
                    botUser.getId(), botUser.getName(), guildCount);
        }
        catch (InterruptedException exception) {
            shutdownImmediately(candidate);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Discord Gateway to become ready",
                    exception);
        }
        catch (RuntimeException exception) {
            shutdownImmediately(candidate);
            throw new IllegalStateException("Failed to connect to the Discord Gateway", exception);
        }
    }

    @Override
    public synchronized void stop() {
        JDA current = jda;
        jda = null;
        running = false;

        if (current == null) {
            return;
        }

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

    @Override
    public boolean isRunning() {
        return running;
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

    record DiscordGatewayState(boolean operational, String connectionStatus, int guildCount) {
    }
}
