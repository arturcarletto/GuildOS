package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordGuildEventListenerTest {

    private final GuildConnectionService service = mock(GuildConnectionService.class);
    private final DiscordGuildCommandRegistrar commandRegistrar = mock(DiscordGuildCommandRegistrar.class);
    private final DiscordGuildChannelCacheSync channelCacheSync = mock(DiscordGuildChannelCacheSync.class);
    private final DiscordGuildEventListener listener =
            new DiscordGuildEventListener(service, commandRegistrar, channelCacheSync);

    @Test
    void readySynchronizesEveryCurrentlyConnectedGuild() {
        JDA jda = mock(JDA.class);
        Guild firstGuild = guild("2001", "First Guild");
        Guild secondGuild = guild("2002", "Second Guild");
        when(jda.getGuilds()).thenReturn(List.of(firstGuild, secondGuild));
        ReadyEvent event = mock(ReadyEvent.class);
        when(event.getJDA()).thenReturn(jda);

        listener.onReady(event);

        verify(service).connect(new ConnectGuildCommand("2001", "First Guild"));
        verify(service).connect(new ConnectGuildCommand("2002", "Second Guild"));
        verify(commandRegistrar).reconcile(firstGuild);
        verify(commandRegistrar).reconcile(secondGuild);
        verify(channelCacheSync).sync(firstGuild);
        verify(channelCacheSync).sync(secondGuild);
    }

    @Test
    void guildJoinConnectsTheGuild() {
        JDA jda = mock(JDA.class);
        Guild guild = guild("2003", "Joined Guild");

        listener.onGuildJoin(new GuildJoinEvent(jda, 1, guild));

        InOrder order = inOrder(service, commandRegistrar, channelCacheSync);
        order.verify(service).connect(new ConnectGuildCommand("2003", "Joined Guild"));
        order.verify(commandRegistrar).reconcile(guild);
        order.verify(channelCacheSync).sync(guild);
    }

    @Test
    void guildLeaveDisconnectsTheGuild() {
        JDA jda = mock(JDA.class);
        Guild guild = guild("2004", "Left Guild");

        listener.onGuildLeave(new GuildLeaveEvent(jda, 1, guild));

        verify(service).disconnect(new DisconnectGuildCommand("2004"));
        verify(commandRegistrar, never()).reconcile(guild);
        verify(channelCacheSync, never()).sync(guild);
    }

    @Test
    void registrationFailureDoesNotPreventGuildConnectionOrEscape() {
        JDA jda = mock(JDA.class);
        Guild guild = guild("2005", "Registration Failure Guild");
        when(guild.updateCommands()).thenThrow(new IllegalStateException("sensitive upstream detail"));
        DiscordGuildEventListener listenerWithRealRegistrar = new DiscordGuildEventListener(
                service,
                new DiscordGuildCommandRegistrar(new DiscordCommandCatalog()),
                channelCacheSync);

        assertThatCode(() -> listenerWithRealRegistrar.onGuildJoin(new GuildJoinEvent(jda, 1, guild)))
                .doesNotThrowAnyException();
        verify(service).connect(new ConnectGuildCommand("2005", "Registration Failure Guild"));
        verify(channelCacheSync).sync(guild);
    }

    private Guild guild(String id, String name) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        when(guild.getName()).thenReturn(name);
        return guild;
    }
}
