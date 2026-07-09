package io.github.arturcarletto.guildos.guildmoderation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GuildModerationConfiguration {

    @Bean
    @ConditionalOnMissingBean(GuildModerationDiscordClient.class)
    GuildModerationDiscordClient unavailableGuildModerationDiscordClient() {
        return new GuildModerationDiscordClient() {
            @Override
            public ModerationActionResult timeoutMember(TimeoutMemberCommand command) {
                throw new ModerationDiscordActionException(ModerationFailureCategory.DISCORD_UNAVAILABLE);
            }

            @Override
            public MemberSearchResult searchMembers(MemberSearchQuery query) {
                throw new ModerationDiscordActionException(ModerationFailureCategory.DISCORD_UNAVAILABLE);
            }
        };
    }
}
