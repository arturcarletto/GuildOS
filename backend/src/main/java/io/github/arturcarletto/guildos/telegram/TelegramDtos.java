package io.github.arturcarletto.guildos.telegram;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal, internal wire models for the small slice of the Telegram Bot API this proof of concept
 * uses ({@code getUpdates} and {@code sendMessage}). Only the fields GuildOS reads are bound; every
 * other field Telegram sends is ignored. These types are package-private on purpose — Telegram wire
 * shapes never leak into platform-neutral code.
 *
 * <p>The {@code text} field is read in memory to detect a command and is never persisted or logged.
 */
final class TelegramDtos {

    private TelegramDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramUpdate(
            @JsonProperty("update_id") long updateId,
            @JsonProperty("message") TelegramMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            @JsonProperty("chat") TelegramChat chat,
            @JsonProperty("from") TelegramUser from,
            @JsonProperty("text") String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramChat(
            @JsonProperty("id") long id,
            @JsonProperty("type") String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramUser(
            @JsonProperty("id") long id,
            @JsonProperty("is_bot") boolean bot,
            @JsonProperty("username") String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramUpdatesResponse(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("result") List<TelegramUpdate> result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TelegramSendMessageResponse(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("result") TelegramMessage result) {
    }

    record TelegramSendMessageRequest(
            @JsonProperty("chat_id") long chatId,
            @JsonProperty("text") String text) {
    }
}
