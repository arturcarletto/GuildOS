package io.github.arturcarletto.guildos.guild;

public record DisconnectGuildCommand(String discordGuildId) {

    private static final int MAX_DISCORD_GUILD_ID_LENGTH = 32;

    public DisconnectGuildCommand {
        if (discordGuildId == null || discordGuildId.isBlank()) {
            throw new IllegalArgumentException("discordGuildId must not be blank");
        }
        if (discordGuildId.length() > MAX_DISCORD_GUILD_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "discordGuildId must not exceed " + MAX_DISCORD_GUILD_ID_LENGTH + " characters");
        }
    }
}
