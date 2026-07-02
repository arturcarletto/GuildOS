package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.junit.jupiter.api.Test;

import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordGuildEventListenerTest {

    private final GuildConnectionService service = mock(GuildConnectionService.class);
    private final DiscordGuildEventListener listener = new DiscordGuildEventListener(service);

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
    }

    @Test
    void guildJoinConnectsTheGuild() {
        JDA jda = mock(JDA.class);
        Guild guild = guild("2003", "Joined Guild");

        listener.onGuildJoin(new GuildJoinEvent(jda, 1, guild));

        verify(service).connect(new ConnectGuildCommand("2003", "Joined Guild"));
    }

    @Test
    void guildLeaveDisconnectsTheGuild() {
        JDA jda = mock(JDA.class);
        Guild guild = guild("2004", "Left Guild");

        listener.onGuildLeave(new GuildLeaveEvent(jda, 1, guild));

        verify(service).disconnect(new DisconnectGuildCommand("2004"));
    }

    private Guild guild(String id, String name) {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(id);
        when(guild.getName()).thenReturn(name);
        return guild;
    }
}
