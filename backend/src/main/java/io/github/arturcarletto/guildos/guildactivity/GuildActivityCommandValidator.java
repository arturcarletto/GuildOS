package io.github.arturcarletto.guildos.guildactivity;

import java.util.regex.Pattern;

final class GuildActivityCommandValidator {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");

    private GuildActivityCommandValidator() {
    }

    static void validate(IngestGuildActivityCommand command) {
        if (command == null) {
            throw new InvalidGuildActivityCommandException("activity command is required");
        }
        requireSourceEventId(command.sourceEventId());
        if (command.eventType() == null) {
            throw new InvalidGuildActivityCommandException("event type is required");
        }
        requireSnowflake(command.discordGuildId(), "guild id");
        requireSnowflake(command.subjectDiscordId(), "subject id");
        requireOptionalSnowflake(command.channelDiscordId(), "channel id");
        requireOptionalSnowflake(command.actorDiscordUserId(), "actor id");
        if (command.occurredAt() == null) {
            throw new InvalidGuildActivityCommandException("occurredAt is required");
        }
        if (command.schemaVersion() != IngestGuildActivityCommand.SCHEMA_VERSION) {
            throw new InvalidGuildActivityCommandException("unsupported activity schema version");
        }
        validateFieldsForType(command);
    }

    static boolean isSnowflake(String value) {
        return value != null && SNOWFLAKE.matcher(value).matches();
    }

    private static void validateFieldsForType(IngestGuildActivityCommand command) {
        switch (command.eventType()) {
            case MEMBER_JOINED, MEMBER_LEFT -> {
                requireAbsent(command.channelDiscordId(), "channel id");
                if (command.actorDiscordUserId() == null || command.actorBot() == null) {
                    throw new InvalidGuildActivityCommandException("member events require actor metadata");
                }
            }
            case MESSAGE_CREATED -> {
                requirePresent(command.channelDiscordId(), "channel id");
                validateActorCoupling(command);
            }
            case MESSAGE_EDITED, MESSAGE_DELETED -> {
                requirePresent(command.channelDiscordId(), "channel id");
                requireAbsent(command.actorDiscordUserId(), "actor id");
                if (command.actorBot() != null) {
                    throw new InvalidGuildActivityCommandException("message update/delete events do not store actors");
                }
            }
        }
    }

    private static void validateActorCoupling(IngestGuildActivityCommand command) {
        if ((command.actorDiscordUserId() == null) != (command.actorBot() == null)) {
            throw new InvalidGuildActivityCommandException("message-created actor fields must both be present or absent");
        }
    }

    private static void requireSourceEventId(String sourceEventId) {
        if (sourceEventId == null || sourceEventId.isBlank() || sourceEventId.length() > 200) {
            throw new InvalidGuildActivityCommandException("source event id is required");
        }
    }

    private static void requireSnowflake(String value, String field) {
        if (!isSnowflake(value)) {
            throw new InvalidGuildActivityCommandException(field + " must be a Discord snowflake");
        }
    }

    private static void requireOptionalSnowflake(String value, String field) {
        if (value != null && !isSnowflake(value)) {
            throw new InvalidGuildActivityCommandException(field + " must be a Discord snowflake");
        }
    }

    private static void requirePresent(String value, String field) {
        if (value == null) {
            throw new InvalidGuildActivityCommandException(field + " is required");
        }
    }

    private static void requireAbsent(String value, String field) {
        if (value != null) {
            throw new InvalidGuildActivityCommandException(field + " is not allowed");
        }
    }
}
