package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;
import java.util.List;

record GuildActivityAnalyticsResponse(
        String guildId,
        Instant from,
        Instant to,
        String bucketTimezone,
        GuildActivityAnalyticsSummary summary,
        List<GuildActivityAnalyticsBucket> buckets) {
}
