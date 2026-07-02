package io.github.arturcarletto.guildos.guild;

public record ConnectGuildCommand(String discordGuildId, String guildName) {

    private static final int MAX_DISCORD_GUILD_ID_LENGTH = 32;
    private static final int MAX_GUILD_NAME_LENGTH = 100;

    public ConnectGuildCommand {
        requireText(discordGuildId, "discordGuildId", MAX_DISCORD_GUILD_ID_LENGTH);
        requireText(guildName, "guildName", MAX_GUILD_NAME_LENGTH);
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
    }
}
