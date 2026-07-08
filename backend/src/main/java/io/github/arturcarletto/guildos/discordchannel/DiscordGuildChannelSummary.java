package io.github.arturcarletto.guildos.discordchannel;

public record DiscordGuildChannelSummary(
        String discordChannelId,
        String name,
        String type,
        String displayName) {

    static DiscordGuildChannelSummary from(DiscordGuildChannel channel) {
        return new DiscordGuildChannelSummary(
                channel.getDiscordChannelId(),
                channel.getName(),
                channel.getType(),
                "#" + channel.getName());
    }
}
