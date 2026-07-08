package io.github.arturcarletto.guildos.telegram;

import java.util.List;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;

/**
 * The tiny slice of the Telegram Bot API this proof of concept needs. Kept package-private so
 * Telegram wire types never escape the adapter.
 */
interface TelegramApiClient {

    /**
     * Fetches pending updates with an id greater than or equal to {@code offset}. Returns an empty
     * list when there are none. Implementations never expose the bot token in exceptions or logs.
     */
    List<TelegramUpdate> getUpdates(long offset);

    /** Sends a plain-text message to a chat. */
    void sendMessage(long chatId, String text);
}
