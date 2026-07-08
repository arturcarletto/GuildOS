package io.github.arturcarletto.guildos.guildactivity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(GuildActivityProcessorProperties.class)
class GuildActivityConfiguration {
}
