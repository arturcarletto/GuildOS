package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;

final class DiscordGuildEventListener extends ListenerAdapter {

    private final GuildConnectionService guildConnectionService;
    private final DiscordGuildCommandRegistrar commandRegistrar;

    DiscordGuildEventListener(
            GuildConnectionService guildConnectionService,
            DiscordGuildCommandRegistrar commandRegistrar) {
        this.guildConnectionService = guildConnectionService;
        this.commandRegistrar = commandRegistrar;
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

    private void connect(Guild guild) {
        guildConnectionService.connect(new ConnectGuildCommand(guild.getId(), guild.getName()));
        commandRegistrar.reconcile(guild);
    }
}
