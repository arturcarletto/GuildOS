package io.github.arturcarletto.guildos.guildmembermessage;

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
@Table(name = "guild_member_message_configurations", schema = "guild_os")
class GuildMemberMessageConfiguration {

    @Id
    private UUID id;

    @Column(name = "registered_guild_id", nullable = false, updatable = false)
    private UUID registeredGuildId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_kind", nullable = false, updatable = false, length = 16)
    private MemberMessageKind messageKind;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "channel_id", nullable = false, length = 20)
    private String channelId;

    @Column(name = "title_template", nullable = false, length = 256)
    private String titleTemplate;

    @Column(name = "description_template", nullable = false, length = 1000)
    private String descriptionTemplate;

    @Column(name = "accent_color", nullable = false)
    private int accentColor;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "footer_template", nullable = false, length = 256)
    private String footerTemplate;

    @Column(name = "mention_member", nullable = false)
    private boolean mentionMember;

    @Column(name = "include_bots", nullable = false)
    private boolean includeBots;

    @Column(name = "button_label", length = 80)
    private String buttonLabel;

    @Column(name = "button_url", length = 512)
    private String buttonUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected GuildMemberMessageConfiguration() {
    }

    /** Applies a configure request, preserving the current enabled state. Returns whether anything changed. */
    boolean configure(String replacementChannelId, MemberMessageAppearance appearance, Instant now) {
        Objects.requireNonNull(replacementChannelId, "replacementChannelId must not be null");
        Objects.requireNonNull(appearance, "appearance must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (channelId.equals(replacementChannelId) && matchesAppearance(appearance)) {
            return false;
        }
        channelId = replacementChannelId;
        titleTemplate = appearance.title();
        descriptionTemplate = appearance.description();
        accentColor = appearance.accentColor();
        imageUrl = appearance.imageUrl();
        footerTemplate = appearance.footer();
        mentionMember = appearance.mentionMember();
        includeBots = appearance.includeBots();
        buttonLabel = appearance.buttonLabel();
        buttonUrl = appearance.buttonUrl();
        updatedAt = now;
        return true;
    }

    /** Flips the enabled state. Always a real change, so callers should flush and bump the version. */
    boolean toggle(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        enabled = !enabled;
        updatedAt = now;
        return true;
    }

    private boolean matchesAppearance(MemberMessageAppearance appearance) {
        return titleTemplate.equals(appearance.title())
                && descriptionTemplate.equals(appearance.description())
                && accentColor == appearance.accentColor()
                && Objects.equals(imageUrl, appearance.imageUrl())
                && footerTemplate.equals(appearance.footer())
                && mentionMember == appearance.mentionMember()
                && includeBots == appearance.includeBots()
                && Objects.equals(buttonLabel, appearance.buttonLabel())
                && Objects.equals(buttonUrl, appearance.buttonUrl());
    }

    MemberMessageAppearance toAppearance() {
        return new MemberMessageAppearance(
                titleTemplate,
                descriptionTemplate,
                accentColor,
                imageUrl,
                footerTemplate,
                mentionMember,
                includeBots,
                buttonLabel,
                buttonUrl);
    }

    UUID getId() {
        return id;
    }

    UUID getRegisteredGuildId() {
        return registeredGuildId;
    }

    MemberMessageKind getMessageKind() {
        return messageKind;
    }

    boolean isEnabled() {
        return enabled;
    }

    String getChannelId() {
        return channelId;
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
