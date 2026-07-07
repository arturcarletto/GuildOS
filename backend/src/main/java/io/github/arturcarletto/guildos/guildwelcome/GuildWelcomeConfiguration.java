package io.github.arturcarletto.guildos.guildwelcome;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "guild_welcome_configurations", schema = "guild_os")
class GuildWelcomeConfiguration {

    @Id
    private UUID id;

    @Column(name = "registered_guild_id", nullable = false, updatable = false, unique = true)
    private UUID registeredGuildId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "channel_id", nullable = false, length = 20)
    private String channelId;

    @Column(name = "message_template", nullable = false, length = 1000)
    private String messageTemplate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected GuildWelcomeConfiguration() {
    }

    boolean configure(String replacementChannelId, String replacementTemplate, Instant now) {
        Objects.requireNonNull(replacementChannelId, "replacementChannelId must not be null");
        Objects.requireNonNull(replacementTemplate, "replacementTemplate must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (enabled
                && channelId.equals(replacementChannelId)
                && messageTemplate.equals(replacementTemplate)) {
            return false;
        }
        enabled = true;
        channelId = replacementChannelId;
        messageTemplate = replacementTemplate;
        updatedAt = now;
        return true;
    }

    boolean disable(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!enabled) {
            return false;
        }
        enabled = false;
        updatedAt = now;
        return true;
    }

    UUID getId() {
        return id;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    boolean isEnabled() {
        return enabled;
    }

    String getChannelId() {
        return channelId;
    }

    String getMessageTemplate() {
        return messageTemplate;
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
