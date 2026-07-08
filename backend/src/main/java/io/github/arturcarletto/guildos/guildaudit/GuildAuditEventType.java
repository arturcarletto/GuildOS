package io.github.arturcarletto.guildos.guildaudit;

public enum GuildAuditEventType {
    GUILD_ONBOARDING_CREATED(
            "Guild access was activated.",
            GuildAuditTargetType.ONBOARDING,
            "Guild access"),
    GUILD_ONBOARDING_REACTIVATED(
            "Guild access was reactivated.",
            GuildAuditTargetType.ONBOARDING,
            "Guild access"),
    GUILD_ACCESS_ROLE_UPDATED(
            "Guild access role was updated.",
            GuildAuditTargetType.ONBOARDING,
            "Guild access"),
    GUILD_ACCESS_REVOKED(
            "Guild access was revoked.",
            GuildAuditTargetType.ONBOARDING,
            "Guild access"),
    GUILD_SETTINGS_UPDATED(
            "Guild settings were updated.",
            GuildAuditTargetType.GUILD_SETTINGS,
            "Guild settings"),
    WELCOME_CONFIGURED(
            "Welcome message configuration was updated.",
            GuildAuditTargetType.WELCOME_MESSAGE,
            "Welcome automation"),
    WELCOME_TOGGLED(
            "Welcome message automation was toggled.",
            GuildAuditTargetType.WELCOME_MESSAGE,
            "Welcome automation"),
    GOODBYE_CONFIGURED(
            "Goodbye message configuration was updated.",
            GuildAuditTargetType.GOODBYE_MESSAGE,
            "Goodbye automation"),
    GOODBYE_TOGGLED(
            "Goodbye message automation was toggled.",
            GuildAuditTargetType.GOODBYE_MESSAGE,
            "Goodbye automation"),
    CHANNEL_METADATA_SYNCED(
            "Discord channel metadata was refreshed.",
            GuildAuditTargetType.CHANNEL_SYNC,
            "Channel metadata"),
    MEMBER_TIMEOUT_CREATED(
            "Member timeout was created.",
            GuildAuditTargetType.MODERATION_ACTION,
            "Member timeout");

    private final String summary;
    private final GuildAuditTargetType targetType;
    private final String targetLabel;

    GuildAuditEventType(String summary, GuildAuditTargetType targetType, String targetLabel) {
        this.summary = summary;
        this.targetType = targetType;
        this.targetLabel = targetLabel;
    }

    String summary() {
        return summary;
    }

    GuildAuditTargetType targetType() {
        return targetType;
    }

    String targetLabel() {
        return targetLabel;
    }
}
