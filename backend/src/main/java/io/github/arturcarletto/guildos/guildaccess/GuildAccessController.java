package io.github.arturcarletto.guildos.guildaccess;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds")
class GuildAccessController {

    private final GuildOnboardingService onboardingService;

    GuildAccessController(GuildOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping
    List<AuthorizedGuildResponse> listAuthorizedGuilds(
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return onboardingService.listAuthorizedGuilds(operator.operatorId());
    }

    @DeleteMapping("/{discordGuildId}/access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeAccess(
            @PathVariable String discordGuildId,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        DiscordSnowflakes.requireValid(discordGuildId);
        onboardingService.revoke(operator.operatorId(), discordGuildId);
    }
}
