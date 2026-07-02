package io.github.arturcarletto.guildos.identity;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the full Spring Security OAuth2 client wiring with OAuth enabled and the Discord
 * Gateway disabled, using fake credentials and never issuing a real request to Discord.
 */
@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=true",
        "guildos.identity.discord-oauth.client-id=fake-client-id",
        "guildos.identity.discord-oauth.client-secret=fake-client-secret-value"
})
@Import(TestcontainersConfiguration.class)
class DiscordOAuthLoginIntegrationTest {

    private static final String CLIENT_SECRET = "fake-client-secret-value";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void authorizationRequestRedirectsToDiscordWithTheConfiguredClientAndScope() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/discord"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();

        URI redirect = URI.create(location);
        assertThat(redirect.getScheme()).isEqualTo("https");
        assertThat(redirect.getHost()).isEqualTo("discord.com");
        assertThat(redirect.getPath()).isEqualTo("/oauth2/authorize");

        Map<String, String> params = queryParameters(redirect);
        assertThat(params).containsEntry("response_type", "code");
        assertThat(params).containsEntry("client_id", "fake-client-id");
        assertThat(params).containsEntry("scope", "identify");
        assertThat(params).containsEntry("redirect_uri", "http://localhost/login/oauth2/code/discord");
        assertThat(params.get("state")).isNotBlank();

        // The client secret must never leak into the browser-visible authorization redirect.
        assertThat(params).doesNotContainKey("client_secret");
        assertThat(params.values()).doesNotContain(CLIENT_SECRET);
        assertThat(location).doesNotContain(CLIENT_SECRET);
    }

    @Test
    void currentOperatorReturnsJsonUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void callbackIsHandledBySpringSecurityWithoutARealTokenExchange() throws Exception {
        // With no saved authorization request in the session, Spring Security fails the login with
        // "authorization_request_not_found" before any token exchange, so no request reaches Discord.
        // The observable result is the standard OAuth2 login-failure redirect, not a 404.
        mockMvc.perform(get("/login/oauth2/code/discord").param("code", "fake").param("state", "fake"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    private static Map<String, String> queryParameters(URI uri) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String name = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            parameters.put(
                    URLDecoder.decode(name, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return parameters;
    }
}