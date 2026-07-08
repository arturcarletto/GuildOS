package io.github.arturcarletto.guildos.telegram;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramSendMessageRequest;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramSendMessageResponse;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdate;
import io.github.arturcarletto.guildos.telegram.TelegramDtos.TelegramUpdatesResponse;

/**
 * {@link TelegramApiClient} backed by Spring's {@link RestClient}, matching the HTTP-client style
 * already used for the Discord guild client.
 *
 * <p>The bot token is embedded once in the base URL ({@code https://api.telegram.org/bot<token>})
 * and is never logged: this class does not log request URLs, and failures are mapped to
 * {@link TelegramApiException} with a generic message that omits the token, URL, and response body.
 */
class RestClientTelegramApiClient implements TelegramApiClient {

    static final String BASE_URL_TEMPLATE = "https://api.telegram.org/bot%s";

    private final RestClient restClient;

    RestClientTelegramApiClient(RestClient.Builder restClientBuilder, TelegramProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL_TEMPLATE.formatted(properties.getBotToken()))
                .build();
    }

    @Override
    public List<TelegramUpdate> getUpdates(long offset) {
        TelegramUpdatesResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/getUpdates").queryParam("offset", offset).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TelegramUpdatesResponse.class);
        }
        catch (ResourceAccessException exception) {
            throw new TelegramApiException("Telegram getUpdates could not be reached");
        }
        catch (RestClientException exception) {
            throw new TelegramApiException("Telegram getUpdates returned an unusable response");
        }

        if (response == null || !response.ok() || response.result() == null) {
            throw new TelegramApiException("Telegram getUpdates returned an unsuccessful response");
        }
        return response.result();
    }

    @Override
    public void sendMessage(long chatId, String text) {
        TelegramSendMessageResponse response;
        try {
            response = restClient.post()
                    .uri("/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new TelegramSendMessageRequest(chatId, text))
                    .retrieve()
                    .body(TelegramSendMessageResponse.class);
        }
        catch (ResourceAccessException exception) {
            throw new TelegramApiException("Telegram sendMessage could not be reached");
        }
        catch (RestClientException exception) {
            throw new TelegramApiException("Telegram sendMessage returned an unusable response");
        }

        if (response == null || !response.ok()) {
            throw new TelegramApiException("Telegram sendMessage returned an unsuccessful response");
        }
    }
}
