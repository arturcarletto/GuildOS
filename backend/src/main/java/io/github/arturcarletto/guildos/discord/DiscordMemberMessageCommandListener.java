package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.util.Collections;
import java.util.Optional;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.ChannelDisplay;
import io.github.arturcarletto.guildos.guildmembermessage.ConfigureMemberMessageCommand;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageConflictException;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageView;
import io.github.arturcarletto.guildos.guildmembermessage.InvalidMemberMessageConfigurationException;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageRenderContext;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

/** Handles the {@code /welcome} and {@code /goodbye} administrative commands with ephemeral embeds. */
final class DiscordMemberMessageCommandListener extends ListenerAdapter {

    private static final Logger logger =
            LoggerFactory.getLogger(DiscordMemberMessageCommandListener.class);
    private static final String GUILD_ONLY_MESSAGE =
            "These messages can only be managed from a server.";
    private static final String PERMISSION_MESSAGE =
            "You need the Manage Server permission to manage these messages.";
    private static final String UNAVAILABLE_MESSAGE =
            "Guild OS is not ready for this server. Please try again later.";
    private static final String ONBOARDING_MESSAGE =
            "This server must be onboarded in Guild OS before these messages can be configured.";
    private static final String CONFLICT_MESSAGE =
            "The configuration changed while this command was running. Run the command again.";
    private static final String FAILURE_MESSAGE =
            "Guild OS could not manage these messages right now. Please try again later.";
    private static final String INVALID_CHANNEL_MESSAGE =
            "Select a standard server text or announcement channel.";
    private static final String CHANNEL_PERMISSION_MESSAGE =
            "Guild OS needs View Channel, Send Messages and Embed Links permissions in that channel.";

    private final GuildMemberMessageService service;
    private final DiscordMemberMessageEmbedFactory embedFactory;
    private final DiscordMemberMessageChannelResolver channelResolver;
    private final Clock clock;

    DiscordMemberMessageCommandListener(
            GuildMemberMessageService service,
            DiscordMemberMessageEmbedFactory embedFactory,
            DiscordMemberMessageChannelResolver channelResolver,
            Clock clock) {
        this.service = service;
        this.embedFactory = embedFactory;
        this.channelResolver = channelResolver;
        this.clock = clock;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        MemberMessageKind kind = kindFor(event.getName());
        if (kind == null) {
            return;
        }
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        String guildId = guild == null ? null : guild.getId();
        String subcommand = event.getSubcommandName();
        try {
            event.deferReply(true).queue(
                    hook -> handle(hook, event, guild, kind, subcommand),
                    failure -> logFailure(kind, subcommand, guildId, "defer", failure));
        } catch (RuntimeException failure) {
            logFailure(kind, subcommand, guildId, "defer", failure);
        }
    }

    private void handle(
            InteractionHook hook,
            SlashCommandInteractionEvent event,
            Guild guild,
            MemberMessageKind kind,
            String subcommand) {
        if (guild == null) {
            replyEmbed(hook, embedFactory.error(GUILD_ONLY_MESSAGE), kind, subcommand, null);
            return;
        }
        String guildId = guild.getId();
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            replyEmbed(hook, embedFactory.error(PERMISSION_MESSAGE), kind, subcommand, guildId);
            return;
        }
        try {
            switch (subcommand == null ? "" : subcommand) {
                case "status" -> status(hook, guild, kind, subcommand);
                case "configure" -> configure(hook, event, guild, kind, subcommand);
                case "preview" -> preview(hook, member, guild, kind, subcommand);
                case "toggle" -> toggle(hook, guild, kind, subcommand);
                default -> replyEmbed(
                        hook, embedFactory.error("Unsupported command."), kind, subcommand, guildId);
            }
        } catch (InvalidMemberMessageConfigurationException failure) {
            replyEmbed(hook, embedFactory.error(failure.getMessage()), kind, subcommand, guildId);
        } catch (GuildMemberMessageConflictException failure) {
            replyEmbed(hook, embedFactory.warning(CONFLICT_MESSAGE), kind, subcommand, guildId);
        } catch (RuntimeException failure) {
            logFailure(kind, subcommand, guildId, "execute", failure);
            replyEmbed(hook, embedFactory.error(FAILURE_MESSAGE), kind, subcommand, guildId);
        }
    }

    private void status(InteractionHook hook, Guild guild, MemberMessageKind kind, String subcommand) {
        GuildMemberMessageView view = service.status(guild.getId(), kind);
        if (handleNonConfigured(hook, view, kind, subcommand, guild.getId())) {
            return;
        }
        ChannelDisplay channel = channelResolver.resolveDisplay(guild, view.channelId());
        replyEmbed(hook, embedFactory.status(view, channel), kind, subcommand, guild.getId());
    }

    private void configure(
            InteractionHook hook,
            SlashCommandInteractionEvent event,
            Guild guild,
            MemberMessageKind kind,
            String subcommand) {
        OptionMapping channelOption = event.getOption("channel");
        OptionMapping messageOption = event.getOption("message");
        if (channelOption == null || messageOption == null) {
            replyEmbed(hook, embedFactory.error("A channel and message are required."),
                    kind, subcommand, guild.getId());
            return;
        }
        GuildChannelUnion channel;
        try {
            channel = channelOption.getAsChannel();
        } catch (RuntimeException failure) {
            replyEmbed(hook, embedFactory.error(INVALID_CHANNEL_MESSAGE), kind, subcommand, guild.getId());
            return;
        }
        if (!DiscordMemberMessageChannelResolver.isAcceptedConfigureChannel(channel, guild)) {
            replyEmbed(hook, embedFactory.error(INVALID_CHANNEL_MESSAGE), kind, subcommand, guild.getId());
            return;
        }
        if (!guild.getSelfMember().hasPermission(
                channel, DiscordMemberMessageChannelResolver.DELIVERY_PERMISSIONS)) {
            replyEmbed(hook, embedFactory.error(CHANNEL_PERMISSION_MESSAGE), kind, subcommand, guild.getId());
            return;
        }

        ConfigureMemberMessageCommand command = new ConfigureMemberMessageCommand(
                channel.getId(),
                messageOption.getAsString(),
                optionalString(event, "title"),
                optionalString(event, "color"),
                optionalString(event, "image"),
                optionalString(event, "footer"),
                optionalBoolean(event, "include-bots"),
                optionalBoolean(event, "mention-member"),
                optionalString(event, "button-label"),
                optionalString(event, "button-url"));

        GuildMemberMessageView view = service.configure(guild.getId(), kind, command);
        if (handleNonConfigured(hook, view, kind, subcommand, guild.getId())) {
            return;
        }
        ChannelDisplay display = channelResolver.resolveDisplay(guild, view.channelId());
        replyEmbed(hook, embedFactory.configured(view, display), kind, subcommand, guild.getId());
    }

    private void preview(
            InteractionHook hook, Member member, Guild guild, MemberMessageKind kind, String subcommand) {
        GuildMemberMessageView view = service.preview(guild.getId(), kind, previewContext(member, guild, kind));
        if (handleNonConfigured(hook, view, kind, subcommand, guild.getId())) {
            return;
        }
        RenderedMemberMessage rendered = view.renderedPreview();
        MessageCreateData previewData =
                embedFactory.previewMessage(rendered, member.getEffectiveAvatarUrl(), clock.instant());
        ChannelDisplay channel = channelResolver.resolveDisplay(guild, view.channelId());
        StringBuilder note = new StringBuilder("🔍 Preview of the ")
                .append(DiscordMemberMessageEmbedFactory.featureName(kind))
                .append(" message. Nothing was sent to ")
                .append(channel.label())
                .append('.');
        rendered.mentionContent().ifPresent(mention -> note.append('\n').append(mention));
        try {
            hook.editOriginal(MessageEditData.fromCreateData(previewData))
                    .setContent(note.toString())
                    .setAllowedMentions(Collections.emptyList())
                    .queue(ignored -> { },
                            failure -> logFailure(kind, subcommand, guild.getId(), "reply", failure));
        } catch (RuntimeException failure) {
            logFailure(kind, subcommand, guild.getId(), "reply", failure);
        }
    }

    private void toggle(InteractionHook hook, Guild guild, MemberMessageKind kind, String subcommand) {
        GuildMemberMessageView view = service.toggle(guild.getId(), kind);
        if (view.state() == io.github.arturcarletto.guildos.guildmembermessage.MemberMessageState.NOT_CONFIGURED) {
            replyEmbed(hook, embedFactory.warning(
                    "Configure this feature first with `/" + DiscordMemberMessageEmbedFactory.rootCommand(kind)
                            + " configure`."),
                    kind, subcommand, guild.getId());
            return;
        }
        if (handleNonConfigured(hook, view, kind, subcommand, guild.getId())) {
            return;
        }
        ChannelDisplay channel = channelResolver.resolveDisplay(guild, view.channelId());
        replyEmbed(hook, embedFactory.toggled(view, channel), kind, subcommand, guild.getId());
    }

    /** Replies with the appropriate embed for a non-configured outcome; returns whether it handled the view. */
    private boolean handleNonConfigured(
            InteractionHook hook,
            GuildMemberMessageView view,
            MemberMessageKind kind,
            String subcommand,
            String guildId) {
        return switch (view.state()) {
            case UNAVAILABLE -> {
                replyEmbed(hook, embedFactory.warning(UNAVAILABLE_MESSAGE), kind, subcommand, guildId);
                yield true;
            }
            case ONBOARDING_REQUIRED -> {
                replyEmbed(hook, embedFactory.warning(ONBOARDING_MESSAGE), kind, subcommand, guildId);
                yield true;
            }
            case NOT_CONFIGURED -> {
                replyEmbed(hook, embedFactory.info(
                        "These messages are not configured yet. Use `/"
                                + DiscordMemberMessageEmbedFactory.rootCommand(kind)
                                + " configure` to set them up."),
                        kind, subcommand, guildId);
                yield true;
            }
            case CONFIGURED -> false;
        };
    }

    private MemberMessageRenderContext previewContext(Member member, Guild guild, MemberMessageKind kind) {
        String mention = kind.supportsMemberMention() ? member.getAsMention() : "";
        return new MemberMessageRenderContext(
                MarkdownSanitizer.escape(member.getEffectiveName()),
                MarkdownSanitizer.escape(member.getUser().getName()),
                MarkdownSanitizer.escape(guild.getName()),
                guild.getMemberCount(),
                mention);
    }

    private void replyEmbed(
            InteractionHook hook,
            MessageEmbed embed,
            MemberMessageKind kind,
            String subcommand,
            String guildId) {
        try {
            hook.editOriginalEmbeds(embed)
                    .setAllowedMentions(Collections.emptyList())
                    .queue(ignored -> { },
                            failure -> logFailure(kind, subcommand, guildId, "reply", failure));
        } catch (RuntimeException failure) {
            logFailure(kind, subcommand, guildId, "reply", failure);
        }
    }

    private static MemberMessageKind kindFor(String commandName) {
        if ("welcome".equals(commandName)) {
            return MemberMessageKind.WELCOME;
        }
        if ("goodbye".equals(commandName)) {
            return MemberMessageKind.GOODBYE;
        }
        return null;
    }

    private static Optional<String> optionalString(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? Optional.empty() : Optional.of(option.getAsString());
    }

    private static Optional<Boolean> optionalBoolean(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? Optional.empty() : Optional.of(option.getAsBoolean());
    }

    private void logFailure(
            MemberMessageKind kind, String subcommand, String guildId, String operation, Throwable failure) {
        logger.warn(
                "Discord member message command failed: kind={}, subcommand={}, guildId={}, "
                        + "operation={}, failureCategory={}",
                kind,
                safe(subcommand),
                safe(guildId),
                operation,
                failure.getClass().getSimpleName());
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
