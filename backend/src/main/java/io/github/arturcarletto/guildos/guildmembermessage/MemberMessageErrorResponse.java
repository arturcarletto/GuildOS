package io.github.arturcarletto.guildos.guildmembermessage;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Consistent JSON error body for the dashboard member-message API. {@code error} is a stable code;
 * {@code message} is an optional, already-safe validation message (the same operator-facing text the
 * slash commands surface) and is omitted when absent. Internal exception detail, class names, ids,
 * and tokens are never included.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberMessageErrorResponse(String error, String message) {

    static MemberMessageErrorResponse of(String error) {
        return new MemberMessageErrorResponse(error, null);
    }

    static MemberMessageErrorResponse of(String error, String message) {
        return new MemberMessageErrorResponse(error, message);
    }
}
