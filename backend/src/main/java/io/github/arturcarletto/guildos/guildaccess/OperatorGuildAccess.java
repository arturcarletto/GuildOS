package io.github.arturcarletto.guildos.guildaccess;

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

/**
 * Persistent authorization relationship granting an operator management access to a registered guild.
 *
 * <p>The relationship is unique per (operator, guild). Revocation is soft (sets {@code revokedAt});
 * a revoked relationship can be reactivated on a later onboarding. The entity holds no Discord or
 * OAuth token data and references the guild only by its internal registry id.
 */
@Entity
@Table(name = "operator_guild_access", schema = "guild_os")
class OperatorGuildAccess {

    @Id
    private UUID id;

    @Column(name = "operator_id", nullable = false, updatable = false)
    private UUID operatorId;

    @Column(name = "registered_guild_id", nullable = false, updatable = false)
    private UUID registeredGuildId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private GuildAccessRole role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OperatorGuildAccess() {
    }

    /**
     * Applies an onboarding for the given role at {@code now}, returning the resulting outcome.
     * When the relationship is already active with the same role, no field is mutated so the
     * timestamp and version are preserved.
     */
    OnboardingOutcome onboard(GuildAccessRole newRole, Instant now) {
        Objects.requireNonNull(newRole, "newRole must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (revokedAt != null) {
            revokedAt = null;
            role = newRole;
            updatedAt = now;
            return OnboardingOutcome.REACTIVATED;
        }
        if (role != newRole) {
            role = newRole;
            updatedAt = now;
            return OnboardingOutcome.ROLE_UPDATED;
        }
        return OnboardingOutcome.UNCHANGED;
    }

    void revoke(Instant now) {
        if (revokedAt != null) {
            return;
        }
        revokedAt = Objects.requireNonNull(now, "now must not be null");
        updatedAt = now;
    }

    boolean isActive() {
        return revokedAt == null;
    }

    UUID getId() {
        return id;
    }

    UUID getOperatorId() {
        return operatorId;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    GuildAccessRole getRole() {
        return role;
    }

    Instant getGrantedAt() {
        return grantedAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    long getVersion() {
        return version;
    }
}
