package io.github.arturcarletto.guildos.guild;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GuildConfiguration {

    @Bean
    Clock guildClock() {
        return Clock.systemUTC();
    }
}
