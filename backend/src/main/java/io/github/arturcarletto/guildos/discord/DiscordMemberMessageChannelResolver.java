package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

/**
 * Resolves a configured member-message channel from JDA and evaluates the bot's delivery
 * permissions. All JDA interaction stays here so the rest of the adapter works with small records.
 */
final class DiscordMemberMessageChannelResolver {

    /** Permissions the bot needs to deliver an embed to a channel. */
    static final Permission[] DELIVERY_PERMISSIONS = {
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS
    };

    /** How a stored channel should be shown in an administrative embed. */
    record ChannelDisplay(String label, String warning) {
    }

    enum DeliveryOutcome {
        READY,
        CHANNEL_UNAVAILABLE,
        PERMISSION_DENIED
    }

    /** The result of resolving a channel for real delivery. {@code channel} is set only when READY. */
    record DeliveryTarget(GuildMessageChannel channel, DeliveryOutcome outcome) {
    }

    ChannelDisplay resolveDisplay(Guild guild, String channelId) {
        GuildMessageChannel channel = messageChannel(guild, channelId);
        if (channel == null || !guild.getSelfMember().hasPermission(channel, DELIVERY_PERMISSIONS)) {
            return new ChannelDisplay("Unavailable", " (deleted or inaccessible)");
        }
        return new ChannelDisplay("#" + MarkdownSanitizer.escape(channel.getName()), "");
    }

    DeliveryTarget resolveDelivery(Guild guild, String channelId) {
        GuildMessageChannel channel = messageChannel(guild, channelId);
        if (channel == null) {
            return new DeliveryTarget(null, DeliveryOutcome.CHANNEL_UNAVAILABLE);
        }
        if (!guild.getSelfMember().hasPermission(channel, DELIVERY_PERMISSIONS)) {
            return new DeliveryTarget(null, DeliveryOutcome.PERMISSION_DENIED);
        }
        return new DeliveryTarget(channel, DeliveryOutcome.READY);
    }

    static boolean isAcceptedConfigureChannel(GuildChannel channel, Guild guild) {
        if (channel == null || !guild.getId().equals(channel.getGuild().getId())) {
            return false;
        }
        ChannelType type = channel.getType();
        return type == ChannelType.TEXT || type == ChannelType.NEWS;
    }

    private static GuildMessageChannel messageChannel(Guild guild, String channelId) {
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel instanceof TextChannel text) {
            return text;
        }
        if (channel instanceof NewsChannel news) {
            return news;
        }
        return null;
    }
}
