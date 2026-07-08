package io.github.arturcarletto.guildos.discordchannel;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;

@RestController
@RequestMapping("/api/v1/guilds/{discordGuildId}/channels")
class DiscordGuildChannelController {

    private final DiscordGuildChannelSyncService service;

    DiscordGuildChannelController(DiscordGuildChannelSyncService service) {
        this.service = service;
    }

    @GetMapping
    DiscordGuildChannelsResponse list(
            @PathVariable String discordGuildId,
            @AuthenticationPrincipal AuthenticatedOperator operator) {
        return service.listActiveChannels(operator.operatorId(), discordGuildId);
    }
}
