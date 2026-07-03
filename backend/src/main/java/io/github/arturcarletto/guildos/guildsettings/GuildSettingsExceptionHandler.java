package io.github.arturcarletto.guildos.guildsettings;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.arturcarletto.guildos.guildaccess.ApiErrorResponse;

@RestControllerAdvice
class GuildSettingsExceptionHandler {

    @ExceptionHandler({InvalidGuildSettingsException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "bad_request");
    }

    @ExceptionHandler(GuildSettingsNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(GuildSettingsNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(GuildSettingsConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(GuildSettingsConflictException exception) {
        return error(HttpStatus.CONFLICT, "conflict");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code));
    }
}
