package io.github.arturcarletto.guildos.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorLoginService {

    private final OperatorAccountRepository repository;
    private final Clock clock;

    OperatorLoginService(OperatorAccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public OperatorIdentity login(OperatorLoginCommand command) {
        Instant loggedInAt = clock.instant();
        repository.insertIfAbsent(
                UUID.randomUUID(),
                command.discordUserId(),
                command.username(),
                command.globalDisplayName(),
                command.avatarHash(),
                loggedInAt);

        OperatorAccount account = repository.findByDiscordUserIdForUpdate(command.discordUserId())
                .orElseThrow(() -> new IllegalStateException("Operator was not available after insert-if-absent"));
        account.login(command, loggedInAt);
        return account.toIdentity();
    }
}
