package io.github.arturcarletto.guildos.guildaudit;

import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/audit-log")
class GuildAuditLogController {

    private final GuildAuditLogService service;

    GuildAuditLogController(GuildAuditLogService service) {
        this.service = service;
    }

    @GetMapping
    GuildAuditLogResponse query(
            @PathVariable String discordGuildId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.query(operator.operatorId(), discordGuildId, limit, eventType, from, to);
    }
}
