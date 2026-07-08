package io.github.arturcarletto.guildos.guildmoderation;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/moderation")
class GuildModerationController {

    private final GuildModerationService service;

    GuildModerationController(GuildModerationService service) {
        this.service = service;
    }

    @PostMapping("/timeout")
    ModerationActionResponse timeoutMember(
            @PathVariable String discordGuildId,
            @Valid @RequestBody TimeoutMemberRequest request,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.timeoutMember(operator.operatorId(), discordGuildId, request);
    }
}
