package io.github.arturcarletto.guildos.guildaudit;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
class GuildAuditLogService {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final GuildAccessAuthorizer authorizer;
    private final GuildAuditStore store;

    GuildAuditLogService(GuildAccessAuthorizer authorizer, GuildAuditStore store) {
        this.authorizer = authorizer;
        this.store = store;
    }

    GuildAuditLogResponse query(
            UUID operatorId,
            String discordGuildId,
            Integer limit,
            String eventType,
            Instant from,
            Instant to) {
        requireSnowflake(discordGuildId);
        int resolvedLimit = resolveLimit(limit);
        GuildAuditEventType resolvedEventType = resolveEventType(eventType);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidGuildAuditLogRequestException();
        }
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(GuildAuditLogNotFoundException::new);
        return new GuildAuditLogResponse(
                access.discordGuildId(),
                store.find(access.registeredGuildId(), resolvedEventType, from, to, resolvedLimit).stream()
                        .map(GuildAuditLogEntryResponse::from)
                        .toList());
    }

    private static int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            throw new InvalidGuildAuditLogRequestException();
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static GuildAuditEventType resolveEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        try {
            return GuildAuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException exception) {
            throw new InvalidGuildAuditLogRequestException();
        }
    }

    private static void requireSnowflake(String discordGuildId) {
        if (discordGuildId == null || !SNOWFLAKE.matcher(discordGuildId).matches()) {
            throw new InvalidGuildAuditLogRequestException();
        }
    }
}
