package io.github.arturcarletto.guildos.discordchannel;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "discord_guild_channels", schema = "guild_os")
class DiscordGuildChannel {

    @Id
    private UUID id;

    @Column(name = "discord_guild_id", nullable = false, updatable = false, length = 32)
    private String discordGuildId;

    @Column(name = "discord_channel_id", nullable = false, updatable = false, length = 32)
    private String discordChannelId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "position")
    private Integer position;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DiscordGuildChannel() {
    }

    UUID getId() {
        return id;
    }

    String getDiscordGuildId() {
        return discordGuildId;
    }

    String getDiscordChannelId() {
        return discordChannelId;
    }

    String getName() {
        return name;
    }

    String getType() {
        return type;
    }

    Integer getPosition() {
        return position;
    }

    boolean isActive() {
        return active;
    }

    Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
