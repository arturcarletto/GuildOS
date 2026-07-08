package io.github.arturcarletto.guildos.discordchannel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.arturcarletto.guildos.guildaccess.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = DiscordGuildChannelController.class)
class DiscordGuildChannelExceptionHandler {

    @ExceptionHandler(DiscordGuildChannelNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(DiscordGuildChannelNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("not_found"));
    }
}
