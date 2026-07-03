package io.github.arturcarletto.guildos.guildaccess;

/**
 * Decides whether an operator is eligible to onboard a Discord guild, based only on trustworthy
 * Discord data: the owner flag and the permission bitset. A client-supplied "is admin" boolean is
 * never trusted.
 */
final class DiscordGuildEligibility {

    private DiscordGuildEligibility() {
    }

    static boolean isEligible(OperatorDiscordGuild guild) {
        if (guild.owner()) {
            return true;
        }
        DiscordPermissions permissions = DiscordPermissions.of(guild.permissions());
        return permissions.hasAdministrator() || permissions.hasManageGuild();
    }

    static GuildAccessRole roleOf(OperatorDiscordGuild guild) {
        return guild.owner() ? GuildAccessRole.OWNER : GuildAccessRole.ADMIN;
    }
}
