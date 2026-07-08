package io.github.arturcarletto.guildos.guildmembermessage;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

/**
 * Authorized dashboard API for welcome/goodbye member-message automation.
 *
 * <p>The {@code kind} path variable is {@code welcome} or {@code goodbye}. Every endpoint is under the
 * authenticated {@code /api/**} policy; the state-changing {@code PUT}/{@code POST} endpoints
 * additionally require the CSRF token. The operator is taken only from the authenticated principal;
 * the request never supplies an operator id, internal guild id, role, or Discord permission claim.
 */
@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/member-messages")
class MemberMessageController {

    private final MemberMessageDashboardService service;

    MemberMessageController(MemberMessageDashboardService service) {
        this.service = service;
    }

    @GetMapping("/{kind}")
    MemberMessageConfigResponse get(
            @PathVariable String discordGuildId,
            @PathVariable String kind,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.get(operator.operatorId(), discordGuildId, parseKind(kind));
    }

    @PutMapping("/{kind}")
    MemberMessageConfigResponse update(
            @PathVariable String discordGuildId,
            @PathVariable String kind,
            @Valid @RequestBody UpdateMemberMessageRequest request,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.configure(operator.operatorId(), discordGuildId, parseKind(kind), request.toCommand());
    }

    @PostMapping("/{kind}/toggle")
    MemberMessageConfigResponse toggle(
            @PathVariable String discordGuildId,
            @PathVariable String kind,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.toggle(operator.operatorId(), discordGuildId, parseKind(kind));
    }

    @PostMapping("/{kind}/preview")
    MemberMessagePreviewResponse preview(
            @PathVariable String discordGuildId,
            @PathVariable String kind,
            @Valid @RequestBody UpdateMemberMessageRequest request,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.preview(operator.operatorId(), discordGuildId, parseKind(kind), request.toCommand());
    }

    private static MemberMessageKind parseKind(String kind) {
        if ("welcome".equalsIgnoreCase(kind)) {
            return MemberMessageKind.WELCOME;
        }
        if ("goodbye".equalsIgnoreCase(kind)) {
            return MemberMessageKind.GOODBYE;
        }
        throw new InvalidMemberMessageConfigurationException("Unknown member message kind");
    }
}
