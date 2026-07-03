package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * Safe, platform-neutral projection of a member-message configuration for administrative Discord
 * responses. It never exposes the internal optimistic-locking version or any database identifier.
 */
public record GuildMemberMessageView(
        MemberMessageState state,
        String guildName,
        MemberMessageKind kind,
        boolean enabled,
        String channelId,
        MemberMessageAppearance appearance,
        RenderedMemberMessage renderedPreview) {

    static GuildMemberMessageView unavailable(MemberMessageKind kind) {
        return empty(MemberMessageState.UNAVAILABLE, kind, null);
    }

    static GuildMemberMessageView onboardingRequired(MemberMessageKind kind, String guildName) {
        return empty(MemberMessageState.ONBOARDING_REQUIRED, kind, guildName);
    }

    static GuildMemberMessageView notConfigured(MemberMessageKind kind, String guildName) {
        return empty(MemberMessageState.NOT_CONFIGURED, kind, guildName);
    }

    static GuildMemberMessageView configured(
            String guildName, StoredGuildMemberMessage stored) {
        return configured(guildName, stored, null);
    }

    static GuildMemberMessageView configured(
            String guildName, StoredGuildMemberMessage stored, RenderedMemberMessage renderedPreview) {
        return new GuildMemberMessageView(
                MemberMessageState.CONFIGURED,
                guildName,
                stored.kind(),
                stored.enabled(),
                stored.channelId(),
                stored.appearance(),
                renderedPreview);
    }

    private static GuildMemberMessageView empty(
            MemberMessageState state, MemberMessageKind kind, String guildName) {
        return new GuildMemberMessageView(state, guildName, kind, false, null, null, null);
    }
}
