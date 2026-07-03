package io.github.arturcarletto.guildos.guildstatus;

public record GuildStatusView(
        GuildStatusState state,
        String guildName,
        boolean connected,
        String timezone,
        String locale,
        long settingsVersion) {

    public static GuildStatusView unavailable() {
        return new GuildStatusView(GuildStatusState.UNAVAILABLE, null, false, null, null, 0);
    }

    public static GuildStatusView notOnboarded(String guildName) {
        return new GuildStatusView(GuildStatusState.NOT_ONBOARDED, guildName, true, null, null, 0);
    }

    public static GuildStatusView active(
            String guildName,
            String timezone,
            String locale,
            long settingsVersion) {
        return new GuildStatusView(
                GuildStatusState.ACTIVE,
                guildName,
                true,
                timezone,
                locale,
                settingsVersion);
    }
}
