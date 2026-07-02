package io.github.arturcarletto.guildos.identity;

import java.util.UUID;

public record CurrentOperatorResponse(
        UUID operatorId,
        String discordUserId,
        String username,
        String displayName,
        String avatarHash) {

    static CurrentOperatorResponse from(AuthenticatedOperator operator) {
        return new CurrentOperatorResponse(
                operator.operatorId(),
                operator.discordUserId(),
                operator.username(),
                operator.displayName(),
                operator.avatarHash());
    }
}
