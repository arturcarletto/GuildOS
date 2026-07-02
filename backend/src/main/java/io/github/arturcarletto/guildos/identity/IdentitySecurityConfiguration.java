package io.github.arturcarletto.guildos.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {

    @Bean
    SecurityFilterChain identitySecurityFilterChain(
            HttpSecurity http,
            DiscordOAuthProperties properties,
            ObjectProvider<DiscordOAuth2UserService> oauth2UserServiceProvider) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/error",
                                "/oauth2/authorization/discord",
                                "/login/oauth2/code/discord")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint()))
                .csrf(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)));

        if (properties.isEnabled()) {
            DiscordOAuth2UserService oauth2UserService = oauth2UserServiceProvider.getObject();
            http.oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(oauth2UserService))
                    .defaultSuccessUrl("/api/v1/me", true));
        }

        return http.build();
    }
}
