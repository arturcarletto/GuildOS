package io.github.arturcarletto.guildos.identity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class CurrentOperatorController {

    @GetMapping
    CurrentOperatorResponse currentOperator(@AuthenticationPrincipal AuthenticatedOperator operator) {
        return CurrentOperatorResponse.from(operator);
    }
}
