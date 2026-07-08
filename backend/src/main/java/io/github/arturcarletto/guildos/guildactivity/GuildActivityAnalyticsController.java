package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/analytics/activity")
class GuildActivityAnalyticsController {

    private final GuildActivityAnalyticsService service;

    GuildActivityAnalyticsController(GuildActivityAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    GuildActivityAnalyticsResponse query(
            @PathVariable String discordGuildId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.query(operator.operatorId(), discordGuildId, from, to);
    }
}
