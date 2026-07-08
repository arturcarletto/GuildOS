package io.github.arturcarletto.guildos.discordchannel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class DiscordGuildChannelStore {

    private static final Pattern SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");
    private static final int MAX_NAME_LENGTH = 100;

    private final DiscordGuildChannelRepository repository;

    DiscordGuildChannelStore(DiscordGuildChannelRepository repository) {
        this.repository = repository;
    }

    @Transactional
    void sync(String discordGuildId, List<DiscordGuildChannelSnapshot> snapshots, Instant syncedAt) {
        requireSnowflake(discordGuildId, "discordGuildId");
        Map<String, DiscordGuildChannelSnapshot> unique = new LinkedHashMap<>();
        for (DiscordGuildChannelSnapshot snapshot : snapshots) {
            DiscordGuildChannelSnapshot normalized = normalize(snapshot);
            unique.put(normalized.discordChannelId(), normalized);
            repository.upsertActive(
                    UUID.randomUUID(),
                    discordGuildId,
                    normalized.discordChannelId(),
                    normalized.name(),
                    normalized.type().name(),
                    normalized.position(),
                    syncedAt);
        }
        if (unique.isEmpty()) {
            repository.markAllInactive(discordGuildId, syncedAt);
        } else {
            repository.markMissingInactive(discordGuildId, unique.keySet(), syncedAt);
        }
    }

    @Transactional(readOnly = true)
    List<DiscordGuildChannelSummary> findActiveSupported(String discordGuildId) {
        requireSnowflake(discordGuildId, "discordGuildId");
        return repository.findActiveSupportedByDiscordGuildId(discordGuildId).stream()
                .map(DiscordGuildChannelSummary::from)
                .toList();
    }

    private static DiscordGuildChannelSnapshot normalize(DiscordGuildChannelSnapshot snapshot) {
        if (snapshot == null || snapshot.type() == null) {
            throw new IllegalArgumentException("Discord channel metadata is required");
        }
        requireSnowflake(snapshot.discordChannelId(), "discordChannelId");
        String name = normalizeName(snapshot.name());
        return new DiscordGuildChannelSnapshot(
                snapshot.discordChannelId(),
                name,
                snapshot.type(),
                snapshot.position());
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Discord channel name is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return trimmed.substring(0, MAX_NAME_LENGTH);
        }
        return trimmed;
    }

    private static void requireSnowflake(String value, String fieldName) {
        if (value == null || !SNOWFLAKE.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a Discord snowflake");
        }
    }
}
