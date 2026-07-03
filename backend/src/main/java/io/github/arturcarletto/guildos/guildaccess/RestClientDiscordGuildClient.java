package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link DiscordGuildClient} backed by Spring's {@link RestClient}. It sends the operator's OAuth
 * access token as a bearer credential and maps Discord failures onto typed exceptions without ever
 * logging or surfacing the token, request headers, or the Discord response body.
 */
class RestClientDiscordGuildClient implements DiscordGuildClient {

    static final String USER_GUILDS_URI = "https://discord.com/api/v10/users/@me/guilds";

    private final RestClient restClient;

    RestClientDiscordGuildClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public List<OperatorDiscordGuild> fetchOperatorGuilds(String accessToken) {
        DiscordGuildResponse[] guilds;
        try {
            guilds = restClient.get()
                    .uri(USER_GUILDS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw mapErrorStatus(response.getStatusCode());
                    })
                    .body(DiscordGuildResponse[].class);
        }
        catch (DiscordReauthenticationRequiredException | DiscordUnavailableException
                | DiscordResponseException exception) {
            throw exception;
        }
        catch (ResourceAccessException exception) {
            throw new DiscordUnavailableException("Discord guild list could not be reached");
        }
        catch (RestClientException exception) {
            throw new DiscordResponseException("Discord returned a malformed guild list");
        }

        if (guilds == null) {
            throw new DiscordResponseException("Discord returned an empty guild list body");
        }
        return Arrays.stream(guilds).map(RestClientDiscordGuildClient::toOperatorGuild).toList();
    }

    private static RuntimeException mapErrorStatus(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()
                || status.value() == HttpStatus.FORBIDDEN.value()) {
            return new DiscordReauthenticationRequiredException("Discord rejected the operator authorization");
        }
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError()) {
            return new DiscordUnavailableException("Discord is temporarily unavailable");
        }
        return new DiscordResponseException("Discord returned an unexpected response status");
    }

    private static OperatorDiscordGuild toOperatorGuild(DiscordGuildResponse response) {
        if (response == null) {
            throw new DiscordResponseException("Discord returned a null guild entry");
        }
        String discordGuildId = requireValidId(response.id());
        if (!StringUtils.hasText(response.name())) {
            throw new DiscordResponseException("Discord guild is missing its name");
        }
        if (response.owner() == null) {
            throw new DiscordResponseException("Discord guild is missing its owner flag");
        }
        BigInteger permissions = parsePermissions(response.permissions());
        String iconHash = StringUtils.hasText(response.icon()) ? response.icon() : null;
        return new OperatorDiscordGuild(discordGuildId, response.name(), iconHash, response.owner(), permissions);
    }

    private static String requireValidId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new DiscordResponseException("Discord guild is missing its id");
        }
        try {
            return DiscordSnowflakes.requireValid(id);
        }
        catch (InvalidDiscordGuildIdException exception) {
            throw new DiscordResponseException("Discord guild id was not a valid snowflake");
        }
    }

    private static BigInteger parsePermissions(String raw) {
        try {
            return DiscordPermissions.parse(raw).value();
        }
        catch (IllegalArgumentException exception) {
            throw new DiscordResponseException("Discord guild permissions were malformed");
        }
    }
}
