package io.github.arturcarletto.guildos.guildactivity;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.arturcarletto.guildos.guildaccess.ApiErrorResponse;

@RestControllerAdvice(assignableTypes = GuildActivityAnalyticsController.class)
class GuildActivityAnalyticsExceptionHandler {

    @ExceptionHandler({
            InvalidGuildActivityAnalyticsRequestException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ConversionFailedException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "bad_request");
    }

    @ExceptionHandler(GuildActivityAnalyticsNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(GuildActivityAnalyticsNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleServerError(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "server_error");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code));
    }
}
