package io.github.arturcarletto.guildos.guildmembermessage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates the dashboard member-message exceptions into consistent JSON error responses. It only
 * handles this feature's own exception types; bean-validation and malformed-body errors fall through
 * to the shared {@code bad_request} handling. No response ever contains a stack trace, internal class
 * name, database id, token, or Discord response body.
 *
 * <p>The validation code carries the already-safe operator-facing message — the same text the
 * {@code /welcome} and {@code /goodbye} slash commands surface.
 */
@RestControllerAdvice
class MemberMessageExceptionHandler {

    @ExceptionHandler(InvalidMemberMessageConfigurationException.class)
    ResponseEntity<MemberMessageErrorResponse> handleInvalid(
            InvalidMemberMessageConfigurationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(MemberMessageErrorResponse.of("bad_request", exception.getMessage()));
    }

    @ExceptionHandler(MemberMessageAccessNotFoundException.class)
    ResponseEntity<MemberMessageErrorResponse> handleNotFound(MemberMessageAccessNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(MemberMessageErrorResponse.of("not_found"));
    }

    @ExceptionHandler(MemberMessageNotConfiguredException.class)
    ResponseEntity<MemberMessageErrorResponse> handleNotConfigured(
            MemberMessageNotConfiguredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(MemberMessageErrorResponse.of("not_configured"));
    }

    @ExceptionHandler(GuildMemberMessageConflictException.class)
    ResponseEntity<MemberMessageErrorResponse> handleConflict(GuildMemberMessageConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(MemberMessageErrorResponse.of("conflict"));
    }
}
