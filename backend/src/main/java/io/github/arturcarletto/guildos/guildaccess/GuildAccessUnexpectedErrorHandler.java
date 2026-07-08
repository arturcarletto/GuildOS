package io.github.arturcarletto.guildos.guildaccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fallback error handler for the guild-access and onboarding controllers.
 *
 * <p>Scoped with {@code assignableTypes} so it only catches exceptions escaping these two
 * controllers, never framework-level 404/405 responses for the rest of the application. Any
 * exception that the specific {@link GuildAccessExceptionHandler} does not map (for example a
 * transient data-access failure or an unexpected runtime error) would otherwise surface as a raw
 * servlet {@code 500} with no JSON body and no diagnostics. This advice logs the failure
 * server-side and returns the same consistent {@code {"error":"server_error"}} shape as the other
 * API errors, without ever exposing the exception message, stack trace, OAuth tokens, or Discord
 * response bodies to the client.
 */
@RestControllerAdvice(assignableTypes = {GuildAccessController.class, OnboardingController.class})
class GuildAccessUnexpectedErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GuildAccessUnexpectedErrorHandler.class);

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        // Log the concrete cause for operators; the response stays generic and non-sensitive.
        log.error("Unexpected error handling a guild-access request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("server_error"));
    }
}
