package io.github.arturcarletto.guildos.guild;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuildConnectionService {

    private final GuildRepository repository;
    private final Clock clock;

    GuildConnectionService(GuildRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void connect(ConnectGuildCommand command) {
        Instant connectedAt = clock.instant();
        repository.insertIfAbsent(
                UUID.randomUUID(),
                command.discordGuildId(),
                command.guildName(),
                connectedAt);

        Guild guild = repository.findByDiscordGuildId(command.discordGuildId())
                .orElseThrow(() -> new IllegalStateException("Guild was not available after insert-if-absent"));
        guild.connect(command.guildName(), connectedAt);
    }

    @Transactional
    public void disconnect(DisconnectGuildCommand command) {
        repository.findByDiscordGuildId(command.discordGuildId())
                .ifPresent(guild -> guild.disconnect(clock.instant()));
    }
}
