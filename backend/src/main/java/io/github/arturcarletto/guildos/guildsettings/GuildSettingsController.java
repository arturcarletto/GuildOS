package io.github.arturcarletto.guildos.guildsettings;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/settings")
class GuildSettingsController {

    private final GuildSettingsService service;

    GuildSettingsController(GuildSettingsService service) {
        this.service = service;
    }

    @GetMapping
    GuildSettingsResponse get(
            @PathVariable String discordGuildId,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.get(operator.operatorId(), discordGuildId);
    }

    @PutMapping
    GuildSettingsResponse update(
            @PathVariable String discordGuildId,
            @Valid @RequestBody UpdateGuildSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.update(
                operator.operatorId(),
                discordGuildId,
                request.timezone(),
                request.locale(),
                request.expectedVersion());
    }
}
