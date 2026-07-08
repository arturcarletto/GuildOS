package io.github.arturcarletto.guildos.guildactivity;

public enum GuildActivityIngestionResult {
    INSERTED("inserted"),
    DUPLICATE("duplicate"),
    IGNORED_UNKNOWN_GUILD("ignored_unknown_guild"),
    IGNORED_NOT_ONBOARDED("ignored_not_onboarded");

    private final String metricTag;

    GuildActivityIngestionResult(String metricTag) {
        this.metricTag = metricTag;
    }

    String metricTag() {
        return metricTag;
    }
}
