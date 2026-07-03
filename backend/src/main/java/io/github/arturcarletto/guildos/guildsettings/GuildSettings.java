package io.github.arturcarletto.guildos.guildsettings;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "guild_settings", schema = "guild_os")
class GuildSettings {

    @Id
    private UUID id;

    @Column(name = "registered_guild_id", nullable = false, updatable = false, unique = true)
    private UUID registeredGuildId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "locale_tag", nullable = false, length = 35)
    private String localeTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected GuildSettings() {
    }

    boolean update(NormalizedGuildSettings replacement, Instant now) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (timezone.equals(replacement.timezone()) && localeTag.equals(replacement.localeTag())) {
            return false;
        }
        timezone = replacement.timezone();
        localeTag = replacement.localeTag();
        updatedAt = now;
        return true;
    }

    UUID getId() {
        return id;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    String getTimezone() {
        return timezone;
    }

    String getLocaleTag() {
        return localeTag;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    long getVersion() {
        return version;
    }
}
