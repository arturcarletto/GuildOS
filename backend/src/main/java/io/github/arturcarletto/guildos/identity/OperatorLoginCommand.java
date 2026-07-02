package io.github.arturcarletto.guildos.identity;

public record OperatorLoginCommand(
        String discordUserId,
        String username,
        String globalDisplayName,
        String avatarHash) {

    public OperatorLoginCommand {
        requireText(discordUserId, "discordUserId", 32);
        requireText(username, "username", 100);
        globalDisplayName = optionalText(globalDisplayName, "globalDisplayName", 100);
        avatarHash = optionalText(avatarHash, "avatarHash", 128);
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
    }

    private static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
