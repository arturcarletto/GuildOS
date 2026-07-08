package io.github.arturcarletto.guildos.telegram;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.platform.CommunityPlatform;
import io.github.arturcarletto.guildos.platform.PlatformChannelId;
import io.github.arturcarletto.guildos.platform.PlatformMessageSender;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramChat;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramMessage;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUser;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramCommandHandlerTest {

    private final RecordingSender sender = new RecordingSender();
    private final TelegramCommandHandler handler =
            new TelegramCommandHandler(sender, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    void pingRepliesWithTheSafeOnlineMessage() {
        handler.handle(update(1L, message(42L, 555L, 7L, "/ping")));

        assertThat(sender.calls).isEqualTo(1);
        assertThat(sender.lastChannel).isEqualTo(PlatformChannelId.of(CommunityPlatform.TELEGRAM, "555"));
        assertThat(sender.lastText).isEqualTo("GuildOS is online.");
    }

    @Test
    void pingWithBotSuffixAndArgumentsStillReplies() {
        handler.handle(update(2L, message(43L, 556L, 7L, "/ping@GuildOsBot now")));

        assertThat(sender.calls).isEqualTo(1);
        assertThat(sender.lastChannel.externalId()).isEqualTo("556");
    }

    @Test
    void unknownMessagesAreIgnored() {
        handler.handle(update(3L, message(44L, 557L, 7L, "hello there")));
        handler.handle(update(4L, message(45L, 557L, 7L, "/status")));

        assertThat(sender.calls).isZero();
    }

    @Test
    void nullAndPartialUpdatesAreHandledSafely() {
        handler.handle(null);
        handler.handle(new TelegramUpdate(5L, null));
        handler.handle(update(6L, new TelegramMessage(46L, null, null, "/ping")));
        handler.handle(update(7L, new TelegramMessage(47L, new TelegramChat(558L, "private"), null, null)));

        assertThat(sender.calls).isZero();
    }

    private static TelegramUpdate update(long updateId, TelegramMessage message) {
        return new TelegramUpdate(updateId, message);
    }

    private static TelegramMessage message(long messageId, long chatId, long userId, String text) {
        return new TelegramMessage(
                messageId,
                new TelegramChat(chatId, "group"),
                new TelegramUser(userId, false, "tester"),
                text);
    }

    private static final class RecordingSender implements PlatformMessageSender {

        private int calls;
        private PlatformChannelId lastChannel;
        private String lastText;

        @Override
        public CommunityPlatform platform() {
            return CommunityPlatform.TELEGRAM;
        }

        @Override
        public void sendText(PlatformChannelId channel, String text) {
            calls++;
            lastChannel = channel;
            lastText = text;
        }
    }
}
