package io.github.arturcarletto.guildos.guildwelcome;

public record GuildWelcomeView(
        GuildWelcomeState state,
        String guildName,
        boolean enabled,
        String channelId,
        String messageTemplate,
        String renderedPreview,
        long version) {

    static GuildWelcomeView unavailable() {
        return empty(GuildWelcomeState.UNAVAILABLE, null);
    }

    static GuildWelcomeView onboardingRequired(String guildName) {
        return empty(GuildWelcomeState.ONBOARDING_REQUIRED, guildName);
    }

    static GuildWelcomeView notConfigured(String guildName) {
        return empty(GuildWelcomeState.NOT_CONFIGURED, guildName);
    }

    static GuildWelcomeView configured(String guildName, StoredGuildWelcome stored) {
        return configured(guildName, stored, null);
    }

    static GuildWelcomeView configured(
            String guildName, StoredGuildWelcome stored, String renderedPreview) {
        return new GuildWelcomeView(
                GuildWelcomeState.CONFIGURED,
                guildName,
                stored.enabled(),
                stored.channelId(),
                stored.messageTemplate(),
                renderedPreview,
                stored.version());
    }

    private static GuildWelcomeView empty(GuildWelcomeState state, String guildName) {
        return new GuildWelcomeView(state, guildName, false, null, null, null, 0);
    }
}
