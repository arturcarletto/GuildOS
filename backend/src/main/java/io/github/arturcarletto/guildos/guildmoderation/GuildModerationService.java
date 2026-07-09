package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
class GuildModerationService {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");
    private static final int DEFAULT_CASE_LIMIT = 50;
    private static final int MAX_CASE_LIMIT = 100;

    private final GuildAccessAuthorizer authorizer;
    private final GuildModerationDiscordClient discordClient;
    private final GuildModerationCaseRecorder caseRecorder;
    private final ModerationCaseStore caseStore;

    GuildModerationService(
            GuildAccessAuthorizer authorizer,
            GuildModerationDiscordClient discordClient,
            GuildModerationCaseRecorder caseRecorder,
            ModerationCaseStore caseStore) {
        this.authorizer = authorizer;
        this.discordClient = discordClient;
        this.caseRecorder = caseRecorder;
        this.caseStore = caseStore;
    }

    ModerationActionResponse timeoutMember(
            UUID operatorId,
            String discordGuildId,
            TimeoutMemberRequest request) {
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(ModerationAccessNotFoundException::new);
        TimeoutMemberCommand command = request.toCommand(access.discordGuildId());
        ModerationActionResult result = discordClient.timeoutMember(command);
        caseRecorder.recordSuccessfulMemberTimeout(access.registeredGuildId(), operatorId, command);
        return ModerationActionResponse.memberTimeout(access.discordGuildId(), command, result);
    }

    MemberSearchResponse searchMembers(
            UUID operatorId,
            String discordGuildId,
            String query,
            Integer limit) {
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(ModerationAccessNotFoundException::new);
        MemberSearchQuery searchQuery = MemberSearchQuery.of(access.discordGuildId(), query, limit);
        MemberSearchResult result = discordClient.searchMembers(searchQuery);
        return MemberSearchResponse.of(access.discordGuildId(), searchQuery, result);
    }

    ModerationCasesResponse listCases(
            UUID operatorId,
            String discordGuildId,
            Integer limit,
            String actionType,
            Instant from,
            Instant to) {
        requireSnowflake(discordGuildId);
        int resolvedLimit = resolveCaseLimit(limit);
        ModerationCaseActionType resolvedActionType = resolveActionType(actionType);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new InvalidModerationActionException("Moderation case filters are invalid.");
        }
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(ModerationAccessNotFoundException::new);
        return new ModerationCasesResponse(
                access.discordGuildId(),
                caseStore.find(
                                access.registeredGuildId(),
                                resolvedActionType,
                                from,
                                to,
                                resolvedLimit)
                        .stream()
                        .map(ModerationCaseResponse::from)
                        .toList());
    }

    private static int resolveCaseLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CASE_LIMIT;
        }
        if (limit < 1 || limit > MAX_CASE_LIMIT) {
            throw new InvalidModerationActionException("Moderation case filters are invalid.");
        }
        return limit;
    }

    private static ModerationCaseActionType resolveActionType(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        try {
            return ModerationCaseActionType.valueOf(actionType);
        } catch (IllegalArgumentException exception) {
            throw new InvalidModerationActionException("Moderation case filters are invalid.");
        }
    }

    private static void requireSnowflake(String discordGuildId) {
        if (discordGuildId == null || !SNOWFLAKE.matcher(discordGuildId).matches()) {
            throw new InvalidModerationActionException("Moderation request is invalid.");
        }
    }
}
