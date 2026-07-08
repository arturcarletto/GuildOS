package io.github.arturcarletto.guildos.discordchannel;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
public class DiscordGuildChannelSyncService {

    private final GuildAccessAuthorizer authorizer;
    private final GuildDirectory guildDirectory;
    private final DiscordGuildChannelStore store;
    private final GuildAuditRecorder auditRecorder;
    private final Clock clock;

    DiscordGuildChannelSyncService(
            GuildAccessAuthorizer authorizer,
            GuildDirectory guildDirectory,
            DiscordGuildChannelStore store,
            GuildAuditRecorder auditRecorder,
            Clock clock) {
        this.authorizer = authorizer;
        this.guildDirectory = guildDirectory;
        this.store = store;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public void syncGuildChannels(String discordGuildId, List<DiscordGuildChannelSnapshot> channels) {
        Instant syncedAt = clock.instant();
        List<DiscordGuildChannelSnapshot> snapshot = List.copyOf(channels);
        boolean changed = store.sync(discordGuildId, snapshot, syncedAt);
        if (changed) {
            guildDirectory.findByDiscordGuildId(discordGuildId)
                    .ifPresent(guild -> auditRecorder.recordSystemEvent(
                            guild.registeredGuildId(),
                            GuildAuditEventType.CHANNEL_METADATA_SYNCED));
        }
    }

    DiscordGuildChannelsResponse listActiveChannels(UUID operatorId, String discordGuildId) {
        authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(DiscordGuildChannelNotFoundException::new);
        return new DiscordGuildChannelsResponse(store.findActiveSupported(discordGuildId));
    }
}
