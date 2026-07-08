package io.github.arturcarletto.guildos.discordchannel;

public record DiscordGuildChannelSnapshot(
        String discordChannelId,
        String name,
        DiscordGuildChannelType type,
        int position) {
}
