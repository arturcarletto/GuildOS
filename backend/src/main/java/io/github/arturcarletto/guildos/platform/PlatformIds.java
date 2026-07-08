package io.github.arturcarletto.guildos.platform;

/**
 * Shared validation for the platform-scoped identifier value objects. Every external id is required
 * to be non-blank and is trimmed so equality is consistent regardless of surrounding whitespace.
 */
final class PlatformIds {

    private PlatformIds() {
    }

    static String requireValid(CommunityPlatform platform, String externalId) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        return externalId.trim();
    }
}
