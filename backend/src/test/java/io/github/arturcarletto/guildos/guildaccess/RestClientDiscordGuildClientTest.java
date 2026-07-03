package io.github.arturcarletto.guildos.guildaccess;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientDiscordGuildClientTest {

    private static final String TOKEN = "super-secret-access-token";

    private MockRestServiceServer server;
    private RestClientDiscordGuildClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientDiscordGuildClient(builder);
    }

    @Test
    void mapsGuildsAndSendsBearerTokenIgnoringUnknownFields() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        "[{\"id\":\"100\",\"name\":\"Guild One\",\"icon\":\"abc123\",\"owner\":true,"
                                + "\"permissions\":\"8\",\"features\":[\"COMMUNITY\"]}]",
                        MediaType.APPLICATION_JSON));

        List<OperatorDiscordGuild> guilds = client.fetchOperatorGuilds(TOKEN);

        assertThat(guilds).hasSize(1);
        OperatorDiscordGuild guild = guilds.get(0);
        assertThat(guild.discordGuildId()).isEqualTo("100");
        assertThat(guild.name()).isEqualTo("Guild One");
        assertThat(guild.iconHash()).isEqualTo("abc123");
        assertThat(guild.owner()).isTrue();
        assertThat(guild.permissions()).isEqualTo(BigInteger.valueOf(8));
        server.verify();
    }

    @Test
    void treatsNullIconAsAbsentWhenOwnerIsPresent() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess(
                        "[{\"id\":\"100\",\"name\":\"Guild\",\"icon\":null,\"owner\":false,\"permissions\":\"0\"}]",
                        MediaType.APPLICATION_JSON));

        OperatorDiscordGuild guild = client.fetchOperatorGuilds(TOKEN).get(0);

        assertThat(guild.iconHash()).isNull();
        assertThat(guild.owner()).isFalse();
        assertThat(guild.permissions()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void unauthorizedResponseRequiresReauthenticationWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordReauthenticationRequiredException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void forbiddenResponseRequiresReauthentication() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordReauthenticationRequiredException.class);
    }

    @Test
    void rateLimitedResponseIsUnavailable() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordUnavailableException.class);
    }

    @Test
    void serverErrorResponseIsUnavailable() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordUnavailableException.class);
    }

    @Test
    void networkFailureIsUnavailableWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(request -> {
                    throw new IOException("connection reset");
                });

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void unexpectedClientErrorIsABadGatewayResponse() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class);
    }

    @Test
    void malformedSuccessBodyIsABadGatewayResponse() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess("{ not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class);
    }

    @Test
    void missingRequiredFieldIsABadGatewayResponse() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess(
                        "[{\"name\":\"Guild\",\"permissions\":\"0\"}]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class);
    }

    @Test
    void malformedPermissionsIsABadGatewayResponse() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess(
                        "[{\"id\":\"1\",\"name\":\"Guild\",\"owner\":false,\"permissions\":\"not-a-number\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class);
    }

    @Test
    void nullGuildEntryIsABadGatewayResponseWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess("[null]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void missingOwnerIsABadGatewayResponseWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess(
                        "[{\"id\":\"100\",\"name\":\"Guild\",\"permissions\":\"0\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void invalidSnowflakeGuildIdIsABadGatewayResponseWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withSuccess(
                        "[{\"id\":\"not-a-snowflake\",\"name\":\"Guild\",\"owner\":true,\"permissions\":\"0\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void redirectResponseIsABadGatewayResponseWithoutLeakingToken() {
        server.expect(requestTo(RestClientDiscordGuildClient.USER_GUILDS_URI))
                .andRespond(withStatus(HttpStatus.FOUND));

        assertThatThrownBy(() -> client.fetchOperatorGuilds(TOKEN))
                .isInstanceOf(DiscordResponseException.class)
                .hasMessageNotContaining(TOKEN);
    }
}
