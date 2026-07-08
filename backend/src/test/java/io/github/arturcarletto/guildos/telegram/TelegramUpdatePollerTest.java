package io.github.arturcarletto.guildos.telegram;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.platform.CommunityPlatform;
import io.github.arturcarletto.guildos.platform.PlatformChannelId;
import io.github.arturcarletto.guildos.platform.PlatformMessageSender;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramChat;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramMessage;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TelegramUpdatePollerTest {

    private static final TelegramProperties PROPERTIES =
            new TelegramProperties(true, "fake-token", Duration.ofSeconds(2));

    @Test
    void pollAdvancesTheOffsetAndDispatchesEachUpdate() {
        QueueingClient client = new QueueingClient();
        client.enqueue(List.of(pingUpdate(4L, 100L), pingUpdate(7L, 101L)));
        RecordingSender sender = new RecordingSender();
        TelegramCommandHandler handler =
                new TelegramCommandHandler(sender, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        TelegramUpdatePoller poller = new TelegramUpdatePoller(PROPERTIES, client, handler);

        poller.pollOnce();

        assertThat(sender.calls).isEqualTo(2);
        assertThat(poller.currentOffset()).isEqualTo(8L);

        // The next poll must ask Telegram for updates after the highest processed id.
        client.enqueue(List.of());
        poller.pollOnce();
        assertThat(client.requestedOffsets).containsExactly(0L, 8L);
    }

    @Test
    void aFailingUpdateDoesNotStopTheRestOrCrashTheLoop() {
        QueueingClient client = new QueueingClient();
        client.enqueue(List.of(pingUpdate(1L, 200L), pingUpdate(2L, 201L)));
        // A sender that always fails simulates a Telegram error while replying.
        PlatformMessageSender failingSender = new PlatformMessageSender() {
            @Override
            public CommunityPlatform platform() {
                return CommunityPlatform.TELEGRAM;
            }

            @Override
            public void sendText(PlatformChannelId channel, String text) {
                throw new TelegramApiException("send failed");
            }
        };
        TelegramCommandHandler handler =
                new TelegramCommandHandler(failingSender, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        TelegramUpdatePoller poller = new TelegramUpdatePoller(PROPERTIES, client, handler);

        assertThatNoException().isThrownBy(poller::pollOnce);
        assertThat(poller.currentOffset()).isEqualTo(3L);
    }

    private static TelegramUpdate pingUpdate(long updateId, long chatId) {
        return new TelegramUpdate(
                updateId,
                new TelegramMessage(updateId, new TelegramChat(chatId, "group"), null, "/ping"));
    }

    private static final class QueueingClient implements TelegramApiClient {

        private final Deque<List<TelegramUpdate>> batches = new ArrayDeque<>();
        private final List<Long> requestedOffsets = new ArrayList<>();

        void enqueue(List<TelegramUpdate> batch) {
            batches.add(batch);
        }

        @Override
        public List<TelegramUpdate> getUpdates(long offset) {
            requestedOffsets.add(offset);
            return batches.isEmpty() ? List.of() : batches.poll();
        }

        @Override
        public void sendMessage(long chatId, String text) {
            // no-op
        }
    }

    private static final class RecordingSender implements PlatformMessageSender {

        private int calls;

        @Override
        public CommunityPlatform platform() {
            return CommunityPlatform.TELEGRAM;
        }

        @Override
        public void sendText(PlatformChannelId channel, String text) {
            calls++;
        }
    }
}
