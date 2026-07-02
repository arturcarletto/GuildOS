package io.github.arturcarletto.guildos.discord;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

final class DiscordHealthIndicator implements HealthIndicator {

    private final DiscordGateway gateway;

    DiscordHealthIndicator(DiscordGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public Health health() {
        DiscordGateway.DiscordGatewayState state = gateway.state();
        Health.Builder health = state.operational() ? Health.up() : Health.outOfService();

        return health
                .withDetail("connectionStatus", state.connectionStatus())
                .withDetail("guildCount", state.guildCount())
                .build();
    }
}
