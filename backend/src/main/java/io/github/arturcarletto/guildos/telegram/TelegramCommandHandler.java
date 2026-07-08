package io.github.arturcarletto.guildos.telegram;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.platform.CommunityPlatform;
import io.github.arturcarletto.guildos.platform.IncomingCommunityEvent;
import io.github.arturcarletto.guildos.platform.PlatformActorId;
import io.github.arturcarletto.guildos.platform.PlatformBotCommand;
import io.github.arturcarletto.guildos.platform.PlatformChannelId;
import io.github.arturcarletto.guildos.platform.PlatformMessageId;
import io.github.arturcarletto.guildos.platform.PlatformMessageSender;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramChat;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramMessage;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;

/**
 * Handles a single Telegram update. This proof of concept supports exactly one command, {@code /ping},
 * which replies "GuildOS is online.". Every other message — including {@code null}/partial updates —
 * is ignored safely and never crashes the poller.
 *
 * <p>The adapter translates the Telegram-specific update into a platform-neutral
 * {@link IncomingCommunityEvent} (safe ids only, never message text) before acting on it, which is the
 * seam a future activity-ingestion bridge would consume. The message text is inspected in memory only
 * to recognize the command and is never logged or persisted.
 */
class TelegramCommandHandler {

    static final PlatformBotCommand PING = PlatformBotCommand.of("ping", "Check that GuildOS is online");
    static final String PING_RESPONSE = "GuildOS is online.";

    private static final Logger log = LoggerFactory.getLogger(TelegramCommandHandler.class);

    private final PlatformMessageSender messageSender;
    private final Clock clock;

    TelegramCommandHandler(PlatformMessageSender messageSender, Clock clock) {
        this.messageSender = messageSender;
        this.clock = clock;
    }

    void handle(TelegramUpdate update) {
        if (update == null) {
            return;
        }
        TelegramMessage message = update.message();
        if (message == null || message.chat() == null) {
            return;
        }
        String text = message.text();
        if (text == null || text.isBlank() || !PING.matches(text)) {
            // Unknown or non-command messages are intentionally ignored.
            return;
        }

        IncomingCommunityEvent event = toIncomingEvent(message);
        log.debug("Handling Telegram command {}", PING.name());
        messageSender.sendText(event.channel(), PING_RESPONSE);
    }

    private IncomingCommunityEvent toIncomingEvent(TelegramMessage message) {
        TelegramChat chat = message.chat();
        PlatformChannelId channel =
                PlatformChannelId.of(CommunityPlatform.TELEGRAM, Long.toString(chat.id()));
        PlatformActorId actor = message.from() == null
                ? null
                : PlatformActorId.of(CommunityPlatform.TELEGRAM, Long.toString(message.from().id()));
        PlatformMessageId messageId =
                PlatformMessageId.of(CommunityPlatform.TELEGRAM, Long.toString(message.messageId()));
        // Telegram chats are not onboarded GuildOS communities yet, so community is left null for now.
        return new IncomingCommunityEvent(
                CommunityPlatform.TELEGRAM, null, channel, actor, messageId, clock.instant());
    }
}
