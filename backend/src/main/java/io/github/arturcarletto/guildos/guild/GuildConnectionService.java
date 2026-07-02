package io.github.arturcarletto.guildos.guild;

import java.time.Clock;
import java.time.Instant;

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
        Guild guild = repository.findByDiscordGuildId(command.discordGuildId())
                .map(existing -> {
                    existing.connect(command.guildName(), connectedAt);
                    return existing;
                })
                .orElseGet(() -> Guild.connected(command.discordGuildId(), command.guildName(), connectedAt));

        repository.save(guild);
    }

    @Transactional
    public void disconnect(DisconnectGuildCommand command) {
        repository.findByDiscordGuildId(command.discordGuildId())
                .ifPresent(guild -> guild.disconnect(clock.instant()));
    }
}
