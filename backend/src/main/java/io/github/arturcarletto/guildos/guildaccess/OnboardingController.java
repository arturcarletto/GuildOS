package io.github.arturcarletto.guildos.guildaccess;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/onboarding/guilds")
class OnboardingController {

    private final GuildOnboardingService onboardingService;
    private final OperatorOAuthAccessTokens accessTokens;

    OnboardingController(GuildOnboardingService onboardingService, OperatorOAuthAccessTokens accessTokens) {
        this.onboardingService = onboardingService;
        this.accessTokens = accessTokens;
    }

    @GetMapping
    List<EligibleGuildResponse> listEligibleGuilds(
            @AuthenticationPrincipal AuthenticatedOperator operator,
            Authentication authentication,
            HttpServletRequest request) {
        String accessToken = accessTokens.currentAccessToken(authentication, request);
        return onboardingService.listEligibleGuilds(operator.operatorId(), accessToken);
    }

    @PostMapping("/{discordGuildId}")
    ResponseEntity<AuthorizedGuildResponse> onboard(
            @PathVariable String discordGuildId,
            @AuthenticationPrincipal AuthenticatedOperator operator,
            Authentication authentication,
            HttpServletRequest request) {
        DiscordSnowflakes.requireValid(discordGuildId);
        String accessToken = accessTokens.currentAccessToken(authentication, request);
        OnboardingResult result = onboardingService.onboard(operator.operatorId(), discordGuildId, accessToken);
        HttpStatus status = result.outcome() == OnboardingOutcome.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.guild());
    }
}
