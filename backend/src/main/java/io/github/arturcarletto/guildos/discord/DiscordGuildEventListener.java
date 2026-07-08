package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;

final class DiscordGuildEventListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGuildEventListener.class);

    private final GuildConnectionService guildConnectionService;
    private final DiscordGuildCommandRegistrar commandRegistrar;
    private final DiscordGuildChannelCacheSync channelCacheSync;

    DiscordGuildEventListener(
            GuildConnectionService guildConnectionService,
            DiscordGuildCommandRegistrar commandRegistrar,
            DiscordGuildChannelCacheSync channelCacheSync) {
        this.guildConnectionService = guildConnectionService;
        this.commandRegistrar = commandRegistrar;
        this.channelCacheSync = channelCacheSync;
    }

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getGuilds().forEach(this::connect);
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        connect(event.getGuild());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        guildConnectionService.disconnect(new DisconnectGuildCommand(event.getGuild().getId()));
    }

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        refreshChannelMetadata(event);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        refreshChannelMetadata(event);
    }

    @Override
    public void onGenericChannelUpdate(GenericChannelUpdateEvent<?> event) {
        refreshChannelMetadata(event);
    }

    private void connect(Guild guild) {
        guildConnectionService.connect(new ConnectGuildCommand(guild.getId(), guild.getName()));
        commandRegistrar.reconcile(guild);
        channelCacheSync.sync(guild);
    }

    private void refreshChannelMetadata(GenericChannelEvent event) {
        String guildId = "unknown";
        try {
            if (!event.isFromGuild()) {
                return;
            }
            Guild guild = event.getGuild();
            guildId = guild.getId();
            channelCacheSync.sync(guild);
        } catch (RuntimeException failure) {
            logger.warn(
                    "Discord guild channel metadata event refresh failed: eventType={}, guildId={}, failureCategory={}",
                    event.getClass().getSimpleName(),
                    guildId,
                    failureCategory(failure));
        }
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category == null || category.isBlank() ? "UnknownFailure" : category;
    }
}
