package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSnapshot;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelSyncService;
import io.github.arturcarletto.guildos.discordchannel.DiscordGuildChannelType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordGuildChannelCacheSyncTest {

    private static final String GUILD_ID = "500000000000000001";

    private final DiscordGuildChannelSyncService service = mock(DiscordGuildChannelSyncService.class);
    private final DiscordGuildChannelCacheSync sync = new DiscordGuildChannelCacheSync(service);

    @Test
    void syncMapsOnlyTextAndNewsChannelsFromJdaCache() {
        Guild guild = guild();
        TextChannel text = textChannel("600000000000000001", "welcome", 2);
        NewsChannel news = newsChannel("600000000000000002", "announcements", 1);
        VoiceChannel voice = mock(VoiceChannel.class);
        when(guild.getChannels()).thenReturn(List.of(voice, text, news));

        sync.sync(guild);

        ArgumentCaptor<List<DiscordGuildChannelSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).syncGuildChannels(eq(GUILD_ID), captor.capture());
        assertThat(captor.getValue())
                .containsExactly(
                        new DiscordGuildChannelSnapshot(
                                "600000000000000001", "welcome", DiscordGuildChannelType.TEXT, 2),
                        new DiscordGuildChannelSnapshot(
                                "600000000000000002", "announcements", DiscordGuildChannelType.NEWS, 1));
    }

    @Test
    void syncFailuresDoNotEscapeGatewayDispatch() {
        Guild guild = guild();
        TextChannel text = textChannel("600000000000000003", "welcome", 1);
        when(guild.getChannels()).thenReturn(List.of(text));
        doThrow(new IllegalStateException("database detail")).when(service).syncGuildChannels(eq(GUILD_ID), any());

        assertThatCode(() -> sync.sync(guild)).doesNotThrowAnyException();
    }

    @Test
    void mappingFailureDoesNotCallStoreOrEscape() {
        Guild guild = guild();
        when(guild.getChannels()).thenThrow(new IllegalStateException("cache detail"));

        assertThatCode(() -> sync.sync(guild)).doesNotThrowAnyException();

        verifyNoInteractions(service);
    }

    private static Guild guild() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn(GUILD_ID);
        return guild;
    }

    private static TextChannel textChannel(String id, String name, int position) {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn(id);
        when(channel.getName()).thenReturn(name);
        when(channel.getPositionRaw()).thenReturn(position);
        return channel;
    }

    private static NewsChannel newsChannel(String id, String name, int position) {
        NewsChannel channel = mock(NewsChannel.class);
        when(channel.getId()).thenReturn(id);
        when(channel.getName()).thenReturn(name);
        when(channel.getPositionRaw()).thenReturn(position);
        return channel;
    }
}
