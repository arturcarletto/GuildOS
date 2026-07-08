package io.github.arturcarletto.guildos.telegram;

/**
 * Raised when a Telegram Bot API call fails. Its message is deliberately generic: it never contains
 * the bot token, request URL, headers, or the raw Telegram response body.
 */
class TelegramApiException extends RuntimeException {

    TelegramApiException(String message) {
        super(message);
    }
}
