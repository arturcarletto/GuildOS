package io.github.arturcarletto.guildos.guildaccess;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OperatorGuildAccessRepository extends JpaRepository<OperatorGuildAccess, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.operator_guild_access (
                id,
                operator_id,
                registered_guild_id,
                role,
                granted_at,
                updated_at,
                revoked_at,
                version
            )
            VALUES (
                :id,
                :operatorId,
                :registeredGuildId,
                :role,
                :grantedAt,
                :grantedAt,
                NULL,
                0
            )
            ON CONFLICT (operator_id, registered_guild_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("operatorId") UUID operatorId,
            @Param("registeredGuildId") UUID registeredGuildId,
            @Param("role") String role,
            @Param("grantedAt") Instant grantedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT access FROM OperatorGuildAccess access
            WHERE access.operatorId = :operatorId AND access.registeredGuildId = :registeredGuildId
            """)
    Optional<OperatorGuildAccess> findByOperatorIdAndRegisteredGuildIdForUpdate(
            @Param("operatorId") UUID operatorId,
            @Param("registeredGuildId") UUID registeredGuildId);

    @Query("""
            SELECT access FROM OperatorGuildAccess access
            WHERE access.operatorId = :operatorId AND access.registeredGuildId = :registeredGuildId
            """)
    Optional<OperatorGuildAccess> findByOperatorIdAndRegisteredGuildId(
            @Param("operatorId") UUID operatorId,
            @Param("registeredGuildId") UUID registeredGuildId);

    @Query("""
            SELECT access FROM OperatorGuildAccess access
            WHERE access.operatorId = :operatorId AND access.revokedAt IS NULL
            """)
    List<OperatorGuildAccess> findActiveByOperatorId(@Param("operatorId") UUID operatorId);

    List<OperatorGuildAccess> findByOperatorIdAndRegisteredGuildIdIn(
            UUID operatorId, Collection<UUID> registeredGuildIds);
}
