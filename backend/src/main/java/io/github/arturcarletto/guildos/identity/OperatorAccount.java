package io.github.arturcarletto.guildos.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "operator_accounts", schema = "guild_os")
class OperatorAccount {

    @Id
    private UUID id;

    @Column(name = "discord_user_id", nullable = false, updatable = false, length = 32, unique = true)
    private String discordUserId;

    @Column(name = "discord_username", nullable = false, length = 100)
    private String username;

    @Column(name = "discord_global_display_name", length = 100)
    private String globalDisplayName;

    @Column(name = "discord_avatar_hash", length = 128)
    private String avatarHash;

    @Column(name = "first_login_at", nullable = false, updatable = false)
    private Instant firstLoginAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OperatorAccount() {
    }

    void login(OperatorLoginCommand command, Instant loggedInAt) {
        username = command.username();
        globalDisplayName = command.globalDisplayName();
        avatarHash = command.avatarHash();
        lastLoginAt = Objects.requireNonNull(loggedInAt, "loggedInAt must not be null");
        updatedAt = loggedInAt;
    }

    OperatorIdentity toIdentity() {
        return new OperatorIdentity(id, discordUserId, username, globalDisplayName, avatarHash);
    }

    UUID getId() {
        return id;
    }

    String getDiscordUserId() {
        return discordUserId;
    }

    String getUsername() {
        return username;
    }

    String getGlobalDisplayName() {
        return globalDisplayName;
    }

    String getAvatarHash() {
        return avatarHash;
    }

    Instant getFirstLoginAt() {
        return firstLoginAt;
    }

    Instant getLastLoginAt() {
        return lastLoginAt;
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
