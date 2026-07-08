package io.github.arturcarletto.guildos.discordchannel;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
public class DiscordGuildChannelSyncService {

    private final GuildAccessAuthorizer authorizer;
    private final DiscordGuildChannelStore store;
    private final Clock clock;

    DiscordGuildChannelSyncService(
            GuildAccessAuthorizer authorizer,
            DiscordGuildChannelStore store,
            Clock clock) {
        this.authorizer = authorizer;
        this.store = store;
        this.clock = clock;
    }

    public void syncGuildChannels(String discordGuildId, List<DiscordGuildChannelSnapshot> channels) {
        store.sync(discordGuildId, List.copyOf(channels), clock.instant());
    }

    DiscordGuildChannelsResponse listActiveChannels(UUID operatorId, String discordGuildId) {
        authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(DiscordGuildChannelNotFoundException::new);
        return new DiscordGuildChannelsResponse(store.findActiveSupported(discordGuildId));
    }
}
