package io.github.arturcarletto.guildos.guildaccess;

/**
 * Guild OS authorization role granted to an operator for a guild.
 *
 * <p>Mapped from Discord eligibility: guild owner maps to {@link #OWNER}; the Discord
 * {@code ADMINISTRATOR} or {@code MANAGE_GUILD} permission maps to {@link #ADMIN}.
 */
enum GuildAccessRole {
    OWNER,
    ADMIN
}
