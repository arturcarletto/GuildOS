package io.github.arturcarletto.guildos.telegram;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;

/**
 * Long-polling proof of concept for the Telegram adapter. It only exists (and therefore only runs)
 * when {@code guildos.telegram.enabled=true}, and it starts and stops with the Spring lifecycle.
 *
 * <p>A single virtual thread repeatedly calls {@code getUpdates}, dispatches each update to the
 * {@link TelegramCommandHandler}, advances the in-memory offset, and sleeps for the configured poll
 * interval. Telegram/HTTP errors are logged safely (never with the token) and never stop the loop; a
 * single bad update cannot crash the poller because each update is handled in isolation.
 *
 * <p>The polling offset is the only mutable state and is confined to the single poll thread.
 */
class TelegramUpdatePoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdatePoller.class);

    private final TelegramProperties properties;
    private final TelegramApiClient client;
    private final TelegramCommandHandler commandHandler;
    private final Object lifecycleMonitor = new Object();

    private volatile boolean running;
    private volatile Thread pollThread;
    private long offset;

    TelegramUpdatePoller(
            TelegramProperties properties,
            TelegramApiClient client,
            TelegramCommandHandler commandHandler) {
        this.properties = properties;
        this.client = client;
        this.commandHandler = commandHandler;
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            running = true;
            pollThread = Thread.ofVirtual()
                    .name("telegram-update-poller")
                    .start(this::runPollLoop);
        }
        log.info("Telegram update poller started (pollInterval={})", properties.getPollInterval());
    }

    @Override
    public void stop() {
        Thread current;
        synchronized (lifecycleMonitor) {
            if (!running) {
                return;
            }
            running = false;
            current = pollThread;
            pollThread = null;
        }
        if (current != null) {
            current.interrupt();
            try {
                current.join(Duration.ofSeconds(5));
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Telegram update poller stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void runPollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                pollOnce();
            }
            catch (TelegramApiException exception) {
                log.warn("Telegram polling error: {}", exception.getMessage());
            }
            catch (RuntimeException exception) {
                log.warn("Unexpected error during Telegram polling", exception);
            }
            if (!sleepQuietly(properties.getPollInterval())) {
                break;
            }
        }
    }

    /**
     * Performs one poll cycle: fetches updates, advances the offset, and dispatches each update to the
     * command handler in isolation. Package-private so it can be exercised deterministically in tests.
     */
    void pollOnce() {
        List<TelegramUpdate> updates = client.getUpdates(offset);
        for (TelegramUpdate update : updates) {
            if (update != null) {
                offset = Math.max(offset, update.updateId() + 1);
            }
            try {
                commandHandler.handle(update);
            }
            catch (RuntimeException exception) {
                // One bad update must not stop the loop. The message is safe (no token, no text).
                log.warn("Failed to handle a Telegram update: {}", exception.getMessage());
            }
        }
    }

    long currentOffset() {
        return offset;
    }

    private boolean sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
            return true;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
