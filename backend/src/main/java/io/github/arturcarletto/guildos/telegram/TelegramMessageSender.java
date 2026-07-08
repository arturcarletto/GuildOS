package io.github.arturcarletto.guildos.telegram;

import io.github.arturcarletto.guildos.platform.CommunityPlatform;
import io.github.arturcarletto.guildos.platform.PlatformChannelId;
import io.github.arturcarletto.guildos.platform.PlatformMessageSender;

/**
 * Telegram implementation of the platform-neutral {@link PlatformMessageSender}. It translates a
 * {@link PlatformChannelId} into a Telegram chat id and delegates to the {@link TelegramApiClient}.
 */
class TelegramMessageSender implements PlatformMessageSender {

    private final TelegramApiClient client;

    TelegramMessageSender(TelegramApiClient client) {
        this.client = client;
    }

    @Override
    public CommunityPlatform platform() {
        return CommunityPlatform.TELEGRAM;
    }

    @Override
    public void sendText(PlatformChannelId channel, String text) {
        if (channel.platform() != CommunityPlatform.TELEGRAM) {
            throw new IllegalArgumentException(
                    "TelegramMessageSender cannot send to a " + channel.platform() + " channel");
        }
        client.sendMessage(parseChatId(channel.externalId()), text);
    }

    private static long parseChatId(String externalId) {
        try {
            return Long.parseLong(externalId.trim());
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Telegram chat id must be numeric");
        }
    }
}
