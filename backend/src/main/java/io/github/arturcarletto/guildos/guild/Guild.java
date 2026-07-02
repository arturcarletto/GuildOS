package io.github.arturcarletto.guildos.guild;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "guilds", schema = "guild_os")
class Guild {

    @Id
    private UUID id;

    @Column(name = "discord_guild_id", nullable = false, updatable = false, length = 32, unique = true)
    private String discordGuildId;

    @Column(name = "guild_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 16)
    private GuildConnectionStatus connectionStatus;

    @Column(name = "first_connected_at", nullable = false, updatable = false)
    private Instant firstConnectedAt;

    @Column(name = "last_connected_at", nullable = false)
    private Instant lastConnectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Guild() {
    }

    void connect(String name, Instant connectedAt) {
        this.name = name;
        connectionStatus = GuildConnectionStatus.CONNECTED;
        lastConnectedAt = Objects.requireNonNull(connectedAt, "connectedAt must not be null");
        disconnectedAt = null;
        updatedAt = connectedAt;
    }

    void disconnect(Instant disconnectedAt) {
        if (connectionStatus == GuildConnectionStatus.DISCONNECTED) {
            return;
        }

        connectionStatus = GuildConnectionStatus.DISCONNECTED;
        this.disconnectedAt = Objects.requireNonNull(disconnectedAt, "disconnectedAt must not be null");
        updatedAt = disconnectedAt;
    }

    UUID getId() {
        return id;
    }

    String getDiscordGuildId() {
        return discordGuildId;
    }

    String getName() {
        return name;
    }

    GuildConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    Instant getFirstConnectedAt() {
        return firstConnectedAt;
    }

    Instant getLastConnectedAt() {
        return lastConnectedAt;
    }

    Instant getDisconnectedAt() {
        return disconnectedAt;
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
