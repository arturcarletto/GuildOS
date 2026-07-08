package io.github.arturcarletto.guildos.guildactivity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

@Service
class GuildActivityAnalyticsService {

    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final GuildAccessAuthorizer authorizer;
    private final GuildActivityAnalyticsStore store;

    GuildActivityAnalyticsService(GuildAccessAuthorizer authorizer, GuildActivityAnalyticsStore store) {
        this.authorizer = authorizer;
        this.store = store;
    }

    GuildActivityAnalyticsResponse query(UUID operatorId, String discordGuildId, Instant from, Instant to) {
        validateRequest(discordGuildId, from, to);
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(GuildActivityAnalyticsNotFoundException::new);
        List<GuildActivityAnalyticsBucket> buckets =
                store.findBuckets(access.registeredGuildId(), from, to);
        return new GuildActivityAnalyticsResponse(
                access.discordGuildId(),
                from,
                to,
                "UTC",
                GuildActivityAnalyticsSummary.from(buckets),
                buckets);
    }

    private static void validateRequest(String discordGuildId, Instant from, Instant to) {
        if (!GuildActivityCommandValidator.isSnowflake(discordGuildId)) {
            throw new InvalidGuildActivityAnalyticsRequestException("guildId must be a Discord snowflake");
        }
        if (from == null || to == null || !from.isBefore(to)) {
            throw new InvalidGuildActivityAnalyticsRequestException("invalid activity analytics time range");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidGuildActivityAnalyticsRequestException("activity analytics range is too large");
        }
    }
}
