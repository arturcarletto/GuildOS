package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSnapshot;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSyncService;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelType;

final class DiscordGuildChannelCacheSync {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGuildChannelCacheSync.class);

    private final DiscordGuildChannelSyncService syncService;

    DiscordGuildChannelCacheSync(DiscordGuildChannelSyncService syncService) {
        this.syncService = syncService;
    }

    void sync(Guild guild) {
        String guildId = "unknown";
        try {
            guildId = guild.getId();
            List<DiscordGuildChannelSnapshot> channels = guild.getChannels().stream()
                    .map(DiscordGuildChannelCacheSync::snapshot)
                    .filter(snapshot -> snapshot != null)
                    .toList();
            syncService.syncGuildChannels(guildId, channels);
        } catch (RuntimeException failure) {
            logger.warn(
                    "Discord guild channel metadata sync failed: guildId={}, failureCategory={}",
                    guildId,
                    failureCategory(failure));
        }
    }

    private static DiscordGuildChannelSnapshot snapshot(GuildChannel channel) {
        if (channel instanceof TextChannel text) {
            return new DiscordGuildChannelSnapshot(
                    text.getId(), text.getName(), DiscordGuildChannelType.TEXT, text.getPositionRaw());
        }
        if (channel instanceof NewsChannel news) {
            return new DiscordGuildChannelSnapshot(
                    news.getId(), news.getName(), DiscordGuildChannelType.NEWS, news.getPositionRaw());
        }
        return null;
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category == null || category.isBlank() ? "UnknownFailure" : category;
    }
}
