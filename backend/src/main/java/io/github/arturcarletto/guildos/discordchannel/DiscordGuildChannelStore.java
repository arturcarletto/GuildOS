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
    boolean sync(String discordGuildId, List<DiscordGuildChannelSnapshot> snapshots, Instant syncedAt) {
        requireSnowflake(discordGuildId, "discordGuildId");
        Map<String, ChannelState> before = activeState(discordGuildId);
        Map<String, DiscordGuildChannelSnapshot> unique = new LinkedHashMap<>();
        for (DiscordGuildChannelSnapshot snapshot : snapshots) {
            DiscordGuildChannelSnapshot normalized = normalize(snapshot);
            unique.put(normalized.discordChannelId(), normalized);
        }
        boolean changed = !before.equals(snapshotState(unique));
        for (DiscordGuildChannelSnapshot normalized : unique.values()) {
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
        return changed;
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

    private Map<String, ChannelState> activeState(String discordGuildId) {
        Map<String, ChannelState> state = new LinkedHashMap<>();
        for (DiscordGuildChannel channel : repository.findActiveSupportedByDiscordGuildId(discordGuildId)) {
            state.put(channel.getDiscordChannelId(), new ChannelState(
                    channel.getName(),
                    DiscordGuildChannelType.valueOf(channel.getType()),
                    channel.getPosition() == null ? 0 : channel.getPosition()));
        }
        return state;
    }

    private static Map<String, ChannelState> snapshotState(
            Map<String, DiscordGuildChannelSnapshot> snapshots) {
        Map<String, ChannelState> state = new LinkedHashMap<>();
        for (DiscordGuildChannelSnapshot snapshot : snapshots.values()) {
            state.put(snapshot.discordChannelId(), new ChannelState(
                    snapshot.name(),
                    snapshot.type(),
                    snapshot.position()));
        }
        return state;
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

    private record ChannelState(String name, DiscordGuildChannelType type, int position) {
    }
}
