package io.github.arturcarletto.guildos.discordchannel;

import java.util.List;

public record DiscordGuildChannelsResponse(List<DiscordGuildChannelSummary> channels) {
}
