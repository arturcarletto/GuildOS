package io.github.arturcarletto.guildos.guildaccess;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class GuildAccessClientConfiguration {

    @Bean
    DiscordGuildClient discordGuildClient(ObjectProvider<RestClient.Builder> restClientBuilder) {
        return new RestClientDiscordGuildClient(restClientBuilder.getIfAvailable(RestClient::builder));
    }
}
