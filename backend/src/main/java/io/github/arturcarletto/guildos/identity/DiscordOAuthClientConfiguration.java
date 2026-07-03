package io.github.arturcarletto.guildos.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DiscordOAuthProperties.class)
class DiscordOAuthClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "guildos.identity.discord-oauth.enabled", havingValue = "true")
    ClientRegistrationRepository discordClientRegistrationRepository(DiscordOAuthProperties properties) {
        ClientRegistration discord = ClientRegistration.withRegistrationId("discord")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.getRedirectUri())
                .scope("identify", "guilds")
                .authorizationUri("https://discord.com/oauth2/authorize")
                .tokenUri("https://discord.com/api/oauth2/token")
                .userInfoUri("https://discord.com/api/v10/users/@me")
                .userNameAttributeName("id")
                .clientName("Discord")
                .build();
        return new InMemoryClientRegistrationRepository(discord);
    }

    @Bean
    @ConditionalOnProperty(name = "guildos.identity.discord-oauth.enabled", havingValue = "true")
    DiscordOAuth2UserService discordOAuth2UserService(OperatorLoginService operatorLoginService) {
        return new DiscordOAuth2UserService(operatorLoginService);
    }
}
