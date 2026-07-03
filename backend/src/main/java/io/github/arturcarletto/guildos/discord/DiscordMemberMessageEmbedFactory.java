package io.github.arturcarletto.guildos.discord;

import java.time.Instant;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.ChannelDisplay;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageView;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageAppearance;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

/**
 * Builds every Discord embed and message this capability sends: polished ephemeral administrative
 * embeds and the real public welcome/goodbye {@link MessageCreateData}. No internal version or
 * database identifier is ever placed into any embed.
 */
final class DiscordMemberMessageEmbedFactory {

    private static final int SUCCESS_GREEN = 0x57F287;
    private static final int NEUTRAL_GRAY = 0x99AAB5;
    private static final int WARNING_AMBER = 0xFEE75C;
    private static final int ERROR_RED = 0xED4245;
    private static final int INFO_BLURPLE = 0x5865F2;

    private static final int FIELD_VALUE_LIMIT = 1024;
    private static final int TITLE_LIMIT = MessageEmbed.TITLE_MAX_LENGTH;
    private static final int DESCRIPTION_LIMIT = MessageEmbed.DESCRIPTION_MAX_LENGTH;
    private static final String TRUNCATION_MARKER = "… (truncated)";

    // ----- Administrative embeds -----

    MessageEmbed info(String message) {
        return simple(INFO_BLURPLE, message);
    }

    MessageEmbed success(String message) {
        return simple(SUCCESS_GREEN, message);
    }

    MessageEmbed warning(String message) {
        return simple(WARNING_AMBER, message);
    }

    MessageEmbed error(String message) {
        return simple(ERROR_RED, message);
    }

    MessageEmbed status(GuildMemberMessageView view, ChannelDisplay channel) {
        MemberMessageAppearance appearance = view.appearance();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(view.enabled() ? SUCCESS_GREEN : NEUTRAL_GRAY)
                .setTitle(featureName(view.kind()) + " configuration")
                .setDescription(view.enabled() ? "**Enabled**" : "**Disabled**")
                .addField("Channel", channel.label() + channel.warning(), false)
                .addField("Title", fieldPreview(appearance.title()), false)
                .addField("Message", fieldPreview(appearance.description()), false)
                .addField("Footer", fieldPreview(appearance.footer()), false)
                .addField("Accent color", formatColor(appearance.accentColor()), true)
                .addField("Image", yesNo(appearance.imageUrl() != null), true);
        if (view.kind().supportsMemberMention()) {
            embed.addField("Mentions member", yesNo(appearance.mentionMember()), true);
        }
        embed.addField("Includes bots", yesNo(appearance.includeBots()), true);
        if (view.kind().supportsButton()) {
            embed.addField("Button", yesNo(appearance.hasButton()), true);
        }
        return embed.build();
    }

    MessageEmbed configured(GuildMemberMessageView view, ChannelDisplay channel) {
        return new EmbedBuilder()
                .setColor(SUCCESS_GREEN)
                .setTitle(featureName(view.kind()) + " messages configured")
                .setDescription("Use `/" + rootCommand(view.kind())
                        + " preview` to see exactly how it will look.")
                .addField("Channel", channel.label() + channel.warning(), false)
                .addField("State", view.enabled() ? "Enabled" : "Disabled", true)
                .build();
    }

    MessageEmbed toggled(GuildMemberMessageView view, ChannelDisplay channel) {
        return new EmbedBuilder()
                .setColor(view.enabled() ? SUCCESS_GREEN : NEUTRAL_GRAY)
                .setTitle(featureName(view.kind()) + " messages " + (view.enabled() ? "enabled" : "disabled"))
                .setDescription(view.enabled()
                        ? "New members will now receive this message."
                        : "This message is paused. Its channel and appearance are preserved.")
                .addField("Channel", channel.label() + channel.warning(), false)
                .build();
    }

    // ----- Public and preview messages (the real delivery structure) -----

    MessageCreateData publicMessage(
            RenderedMemberMessage rendered, String avatarUrl, Instant timestamp, String mentionUserId) {
        return buildMessage(rendered, avatarUrl, timestamp, mentionUserId);
    }

    MessageCreateData previewMessage(
            RenderedMemberMessage rendered, String avatarUrl, Instant timestamp) {
        // Same structure as real delivery, but no user is whitelisted so nobody is notified.
        return buildMessage(rendered, avatarUrl, timestamp, null);
    }

    private MessageCreateData buildMessage(
            RenderedMemberMessage rendered, String avatarUrl, Instant timestamp, String mentionUserId) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(rendered.accentColor())
                .setTitle(truncate(rendered.title(), TITLE_LIMIT))
                .setDescription(truncate(rendered.description(), DESCRIPTION_LIMIT))
                .setThumbnail(avatarUrl)
                .setFooter(truncate(rendered.footer(), MessageEmbed.TEXT_MAX_LENGTH))
                .setTimestamp(timestamp)
                .addField("Member count", formatCount(rendered.memberCount()), false);
        rendered.optionalImageUrl().ifPresent(embed::setImage);

        MessageCreateBuilder builder = new MessageCreateBuilder()
                .setAllowedMentions(java.util.Collections.emptyList())
                .setEmbeds(embed.build());
        rendered.mentionContent().ifPresent(content -> {
            builder.setContent(content);
            if (mentionUserId != null) {
                builder.mentionUsers(mentionUserId);
            }
        });
        if (rendered.hasButton()) {
            builder.setComponents(ActionRow.of(Button.link(rendered.buttonUrl(), rendered.buttonLabel())));
        }
        return builder.build();
    }

    static String featureName(MemberMessageKind kind) {
        return kind == MemberMessageKind.WELCOME ? "Welcome" : "Goodbye";
    }

    static String rootCommand(MemberMessageKind kind) {
        return kind == MemberMessageKind.WELCOME ? "welcome" : "goodbye";
    }

    private MessageEmbed simple(int color, String message) {
        return new EmbedBuilder().setColor(color).setDescription(message).build();
    }

    private static String fieldPreview(String template) {
        return truncateField(MarkdownSanitizer.escape(template));
    }

    private static String formatColor(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static String formatCount(int count) {
        return String.format("%,d", count);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String truncateField(String value) {
        return truncate(value, FIELD_VALUE_LIMIT);
    }

    private static String truncate(String value, int limit) {
        if (value.length() <= limit) {
            return value;
        }
        int keep = Math.max(0, limit - TRUNCATION_MARKER.length());
        if (keep > 0 && Character.isHighSurrogate(value.charAt(keep - 1))) {
            keep--;
        }
        return value.substring(0, keep) + TRUNCATION_MARKER;
    }
}
