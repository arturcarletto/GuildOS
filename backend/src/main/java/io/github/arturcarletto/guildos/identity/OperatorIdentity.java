package io.github.arturcarletto.guildos.identity;

import java.util.UUID;

public record OperatorIdentity(
        UUID operatorId,
        String discordUserId,
        String username,
        String displayName,
        String avatarHash) {
}
