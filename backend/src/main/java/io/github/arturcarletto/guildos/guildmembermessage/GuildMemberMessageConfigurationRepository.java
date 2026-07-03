package io.github.arturcarletto.guildos.guildmembermessage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GuildMemberMessageConfigurationRepository
        extends JpaRepository<GuildMemberMessageConfiguration, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO guild_os.guild_member_message_configurations (
                id,
                registered_guild_id,
                message_kind,
                enabled,
                channel_id,
                title_template,
                description_template,
                accent_color,
                image_url,
                footer_template,
                mention_member,
                include_bots,
                button_label,
                button_url,
                created_at,
                updated_at,
                version
            )
            VALUES (
                :id,
                :registeredGuildId,
                :messageKind,
                TRUE,
                :channelId,
                :titleTemplate,
                :descriptionTemplate,
                :accentColor,
                :imageUrl,
                :footerTemplate,
                :mentionMember,
                :includeBots,
                :buttonLabel,
                :buttonUrl,
                :now,
                :now,
                0
            )
            ON CONFLICT (registered_guild_id, message_kind) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("registeredGuildId") UUID registeredGuildId,
            @Param("messageKind") String messageKind,
            @Param("channelId") String channelId,
            @Param("titleTemplate") String titleTemplate,
            @Param("descriptionTemplate") String descriptionTemplate,
            @Param("accentColor") int accentColor,
            @Param("imageUrl") String imageUrl,
            @Param("footerTemplate") String footerTemplate,
            @Param("mentionMember") boolean mentionMember,
            @Param("includeBots") boolean includeBots,
            @Param("buttonLabel") String buttonLabel,
            @Param("buttonUrl") String buttonUrl,
            @Param("now") Instant now);

    Optional<GuildMemberMessageConfiguration> findByRegisteredGuildIdAndMessageKind(
            UUID registeredGuildId, MemberMessageKind messageKind);
}
