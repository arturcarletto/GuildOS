package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscordHealthIndicatorTest {

    @Test
    void reportsUpForAConnectedGateway() throws Exception {
        DiscordGateway gateway = startedGateway(JDA.Status.CONNECTED, 2);

        Health health = new DiscordHealthIndicator(gateway).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("connectionStatus", "CONNECTED")
                .containsEntry("guildCount", 2);
    }

    @Test
    void reportsOutOfServiceForADisconnectedGateway() throws Exception {
        DiscordGateway gateway = startedGateway(JDA.Status.DISCONNECTED, 1);

        Health health = new DiscordHealthIndicator(gateway).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails())
                .containsEntry("connectionStatus", "DISCONNECTED")
                .containsEntry("guildCount", 1);
    }

    private DiscordGateway startedGateway(JDA.Status status, int guildCount) throws Exception {
        JDA jda = mock(JDA.class);
        SelfUser selfUser = mock(SelfUser.class);
        List<Guild> guilds = java.util.stream.IntStream.range(0, guildCount)
                .mapToObj(index -> mock(Guild.class))
                .toList();
        when(jda.awaitReady()).thenReturn(jda);
        when(jda.getSelfUser()).thenReturn(selfUser);
        when(selfUser.getId()).thenReturn("123456789");
        when(selfUser.getName()).thenReturn("guild-os-test");
        when(jda.getGuilds()).thenReturn(guilds);
        when(jda.getStatus()).thenReturn(status);

        DiscordGateway gateway = new DiscordGateway(
                new DiscordProperties(true, "test-token"),
                token -> jda);
        gateway.start();
        return gateway;
    }
}
