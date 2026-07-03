package io.github.arturcarletto.guildos.guildaccess;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates guildaccess application and adapter exceptions into consistent JSON error responses.
 * It never exposes internal exception messages, OAuth tokens, client secrets, or Discord response
 * bodies.
 */
@RestControllerAdvice
class GuildAccessExceptionHandler {

    @ExceptionHandler(InvalidDiscordGuildIdException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidGuildId(InvalidDiscordGuildIdException exception) {
        return error(HttpStatus.BAD_REQUEST, "bad_request");
    }

    @ExceptionHandler(DiscordReauthenticationRequiredException.class)
    ResponseEntity<ApiErrorResponse> handleReauthentication(DiscordReauthenticationRequiredException exception) {
        return error(HttpStatus.UNAUTHORIZED, "unauthorized");
    }

    @ExceptionHandler(OperatorGuildEligibilityException.class)
    ResponseEntity<ApiErrorResponse> handleEligibility(OperatorGuildEligibilityException exception) {
        return error(HttpStatus.FORBIDDEN, "forbidden");
    }

    @ExceptionHandler(GuildNotOnboardableException.class)
    ResponseEntity<ApiErrorResponse> handleNotOnboardable(GuildNotOnboardableException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(DiscordResponseException.class)
    ResponseEntity<ApiErrorResponse> handleDiscordResponse(DiscordResponseException exception) {
        return error(HttpStatus.BAD_GATEWAY, "bad_gateway");
    }

    @ExceptionHandler(DiscordUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleDiscordUnavailable(DiscordUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code));
    }
}
