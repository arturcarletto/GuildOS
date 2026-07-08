package io.github.arturcarletto.guildos.guildmoderation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ModerationErrorResponse(String error, String message) {

    static ModerationErrorResponse of(String error) {
        return new ModerationErrorResponse(error, null);
    }

    static ModerationErrorResponse of(String error, String message) {
        return new ModerationErrorResponse(error, message);
    }
}
