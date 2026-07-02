package io.github.arturcarletto.guildos.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OperatorAccountRepository extends JpaRepository<OperatorAccount, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.operator_accounts (
                id,
                discord_user_id,
                discord_username,
                discord_global_display_name,
                discord_avatar_hash,
                first_login_at,
                last_login_at,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :id,
                :discordUserId,
                :username,
                :globalDisplayName,
                :avatarHash,
                :loggedInAt,
                :loggedInAt,
                :loggedInAt,
                :loggedInAt,
                0
            )
            ON CONFLICT (discord_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("discordUserId") String discordUserId,
            @Param("username") String username,
            @Param("globalDisplayName") String globalDisplayName,
            @Param("avatarHash") String avatarHash,
            @Param("loggedInAt") Instant loggedInAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM OperatorAccount account WHERE account.discordUserId = :discordUserId")
    Optional<OperatorAccount> findByDiscordUserIdForUpdate(@Param("discordUserId") String discordUserId);

    Optional<OperatorAccount> findByDiscordUserId(String discordUserId);
}
