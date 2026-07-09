package io.github.arturcarletto.guildos.guildmoderation;

import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = GuildModerationController.class)
class GuildModerationExceptionHandler {

    @ExceptionHandler({
            InvalidModerationActionException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConversionFailedException.class
    })
    ResponseEntity<ModerationErrorResponse> handleBadRequest(Exception exception) {
        String message = exception instanceof InvalidModerationActionException invalid
                ? invalid.getMessage()
                : "Moderation request is invalid.";
        return error(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    @ExceptionHandler(ModerationAccessNotFoundException.class)
    ResponseEntity<ModerationErrorResponse> handleNotFound(ModerationAccessNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "not_found", null);
    }

    @ExceptionHandler(ModerationDiscordActionException.class)
    ResponseEntity<ModerationErrorResponse> handleDiscordFailure(ModerationDiscordActionException exception) {
        return switch (exception.category()) {
            case GUILD_UNAVAILABLE -> error(
                    HttpStatus.BAD_GATEWAY,
                    "guild_unavailable",
                    "The Discord guild is unavailable to the bot.");
            case TARGET_NOT_FOUND -> error(
                    HttpStatus.CONFLICT,
                    "target_not_found",
                    "The target member is not available in this guild.");
            case BOT_PERMISSION_MISSING -> error(
                    HttpStatus.CONFLICT,
                    "bot_permission_missing",
                    "The bot cannot timeout members in this guild.");
            case DISCORD_REJECTED -> error(
                    HttpStatus.BAD_GATEWAY,
                    "discord_rejected",
                    "Discord rejected the moderation action.");
            case RATE_LIMITED -> error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "discord_rate_limited",
                    "Discord rate limited the moderation action.");
            case DISCORD_UNAVAILABLE -> error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "discord_unavailable",
                    "Discord is unavailable. Please try again shortly.");
        };
    }

    private static ResponseEntity<ModerationErrorResponse> error(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(message == null
                ? ModerationErrorResponse.of(code)
                : ModerationErrorResponse.of(code, message));
    }
}
