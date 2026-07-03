package io.github.arturcarletto.guildos.discord;

import java.util.Collections;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeConflictException;
import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeService;
import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeView;
import io.github.arturcarletto.guildos.guildwelcome.InvalidWelcomeTemplateException;
import io.github.arturcarletto.guildos.guildwelcome.WelcomePreviewContext;

final class DiscordWelcomeCommandListener extends ListenerAdapter {

    private static final Logger logger =
            LoggerFactory.getLogger(DiscordWelcomeCommandListener.class);
    private static final String COMMAND_NAME = "welcome";
    private static final String GUILD_ONLY_MESSAGE =
            "Welcome messages can only be managed from a server.";
    private static final String PERMISSION_MESSAGE =
            "You need the Manage Server permission to manage welcome messages.";
    private static final String UNAVAILABLE_MESSAGE =
            "Guild OS is not ready for this server. Please try again later.";
    private static final String ONBOARDING_MESSAGE =
            "This server must be onboarded in Guild OS before welcome messages can be configured.";
    private static final String NOT_CONFIGURED_MESSAGE = """
            Welcome messages are not configured.
            Use /welcome configure to create the configuration.
            """.stripTrailing();
    private static final String CONFLICT_MESSAGE =
            "The welcome configuration changed while this command was running. Run the command again.";
    private static final String FAILURE_MESSAGE =
            "Guild OS could not manage welcome messages right now. Please try again later.";
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final String TEMPLATE_TRUNCATION_MARKER = "... (template truncated)";

    private final GuildWelcomeService welcomeService;

    DiscordWelcomeCommandListener(GuildWelcomeService welcomeService) {
        this.welcomeService = welcomeService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        String guildId = guild == null ? null : guild.getId();
        String subcommand = event.getSubcommandName();
        try {
            event.deferReply(true).queue(
                    hook -> handle(hook, event, guild, subcommand),
                    failure -> logFailure(subcommand, guildId, "defer", failure));
        } catch (RuntimeException failure) {
            logFailure(subcommand, guildId, "defer", failure);
        }
    }

    private void handle(
            InteractionHook hook,
            SlashCommandInteractionEvent event,
            Guild guild,
            String subcommand) {
        if (guild == null) {
            reply(hook, GUILD_ONLY_MESSAGE, subcommand, null);
            return;
        }
        String guildId = guild.getId();
        Member member = event.getMember();
        if (member == null) {
            reply(hook, PERMISSION_MESSAGE, subcommand, guildId);
            return;
        }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            reply(hook, PERMISSION_MESSAGE, subcommand, guildId);
            return;
        }

        try {
            String message = switch (subcommand == null ? "" : subcommand) {
                case "status" -> renderStatus(welcomeService.status(guildId), guild);
                case "configure" -> configure(event, guild);
                case "preview" -> preview(member, guild);
                case "disable" -> renderDisable(welcomeService.disable(guildId));
                default -> "Unsupported welcome command.";
            };
            reply(hook, message, subcommand, guildId);
        } catch (InvalidWelcomeTemplateException failure) {
            reply(
                    hook,
                    "The welcome message is invalid. Check its length, placeholders, and mentions.",
                    subcommand,
                    guildId);
        } catch (GuildWelcomeConflictException failure) {
            reply(hook, CONFLICT_MESSAGE, subcommand, guildId);
        } catch (RuntimeException failure) {
            logFailure(subcommand, guildId, "execute", failure);
            reply(hook, FAILURE_MESSAGE, subcommand, guildId);
        }
    }

    private String configure(SlashCommandInteractionEvent event, Guild guild) {
        OptionMapping channelOption = event.getOption("channel");
        OptionMapping messageOption = event.getOption("message");
        if (channelOption == null || messageOption == null) {
            return "A channel and welcome message are required.";
        }

        GuildChannelUnion channel;
        try {
            channel = channelOption.getAsChannel();
        } catch (RuntimeException failure) {
            return "Select a standard server text or announcement channel.";
        }
        if (!isAcceptedChannel(channel, guild)) {
            return "Select a standard server text or announcement channel.";
        }
        Member selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(channel, Permission.VIEW_CHANNEL)
                || !selfMember.hasPermission(channel, Permission.MESSAGE_SEND)) {
            return "Guild OS needs View Channel and Send Messages permissions in that channel.";
        }

        GuildWelcomeView configured = welcomeService.configure(
                guild.getId(), channel.getId(), messageOption.getAsString());
        return switch (configured.state()) {
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
            case ONBOARDING_REQUIRED -> ONBOARDING_MESSAGE;
            case NOT_CONFIGURED -> NOT_CONFIGURED_MESSAGE;
            case CONFIGURED -> """
                    Welcome messages are configured and enabled.
                    Channel: #%s
                    Version: %d
                    """.formatted(
                            MarkdownSanitizer.escape(channel.getName()),
                            configured.version()).stripTrailing();
        };
    }

    private String preview(Member member, Guild guild) {
        GuildWelcomeView preview = welcomeService.preview(
                guild.getId(),
                new WelcomePreviewContext(
                        MarkdownSanitizer.escape(member.getEffectiveName()),
                        MarkdownSanitizer.escape(guild.getName()),
                        guild.getMemberCount()));
        return switch (preview.state()) {
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
            case ONBOARDING_REQUIRED -> ONBOARDING_MESSAGE;
            case NOT_CONFIGURED -> NOT_CONFIGURED_MESSAGE;
            case CONFIGURED -> {
                ResolvedChannel channel = resolveStoredChannel(guild, preview.channelId());
                String configuration = preview.enabled() ? "Enabled" : "Disabled (preview only)";
                yield """
                        Welcome preview
                        Destination: %s
                        Configuration: %s%s

                        %s
                        """.formatted(
                                channel.displayName(),
                                configuration,
                                channel.warning(),
                                preview.renderedPreview()).stripTrailing();
            }
        };
    }

    private String renderStatus(GuildWelcomeView status, Guild guild) {
        return switch (status.state()) {
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
            case ONBOARDING_REQUIRED -> ONBOARDING_MESSAGE;
            case NOT_CONFIGURED -> NOT_CONFIGURED_MESSAGE;
            case CONFIGURED -> renderConfiguredStatus(status, guild);
        };
    }

    /**
     * Formats a configured `/welcome status` response so that only the displayed template is ever
     * truncated. The heading, server, status, channel, and version metadata always survive; the
     * escaped template is fitted into whatever Discord message budget remains after the fixed
     * prefix and suffix. This deliberately does not rely on the generic whole-message bound, which
     * would blindly cut structural metadata off the end of a Markdown-heavy template.
     */
    private String renderConfiguredStatus(GuildWelcomeView status, Guild guild) {
        ResolvedChannel channel = resolveStoredChannel(guild, status.channelId());
        String prefix = """
                Welcome configuration
                Server: %s
                Status: %s
                Channel: %s%s
                Template:\s""".formatted(
                        MarkdownSanitizer.escape(status.guildName()),
                        status.enabled() ? "Enabled" : "Disabled",
                        channel.displayName(),
                        channel.warning());
        String suffix = "\nVersion: %d".formatted(status.version());
        String escapedTemplate = MarkdownSanitizer.escape(status.messageTemplate());
        int budget = DISCORD_MESSAGE_LIMIT - prefix.length() - suffix.length();
        String templateSection = escapedTemplate.length() <= budget
                ? escapedTemplate
                : truncateTemplateForDisplay(escapedTemplate, budget);
        return prefix + templateSection + suffix;
    }

    /**
     * Truncates an escaped template to fit {@code budget} characters including the explicit
     * truncation marker. Deterministic, never throws for a small budget, never splits a UTF-16
     * surrogate pair, and avoids ending on a dangling Markdown escape backslash when practical.
     */
    private static String truncateTemplateForDisplay(String escapedTemplate, int budget) {
        int available = budget - TEMPLATE_TRUNCATION_MARKER.length();
        if (available <= 0) {
            // Not even the marker fits; keep as much of it as the budget allows.
            return TEMPLATE_TRUNCATION_MARKER.substring(0, Math.max(0, budget));
        }
        int end = available;
        if (Character.isHighSurrogate(escapedTemplate.charAt(end - 1))) {
            end--;
        }
        end = trimDanglingEscape(escapedTemplate, end);
        return escapedTemplate.substring(0, end) + TEMPLATE_TRUNCATION_MARKER;
    }

    private static int trimDanglingEscape(String value, int end) {
        int backslashes = 0;
        int index = end - 1;
        while (index >= 0 && value.charAt(index) == '\\') {
            backslashes++;
            index--;
        }
        // An odd run of trailing backslashes leaves a dangling escape; drop the last one.
        return backslashes % 2 == 1 ? end - 1 : end;
    }

    private String renderDisable(GuildWelcomeView disabled) {
        return switch (disabled.state()) {
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
            case ONBOARDING_REQUIRED -> ONBOARDING_MESSAGE;
            case NOT_CONFIGURED -> NOT_CONFIGURED_MESSAGE;
            case CONFIGURED -> """
                    Welcome messages are disabled.
                    Version: %d
                    """.formatted(disabled.version()).stripTrailing();
        };
    }

    private static boolean isAcceptedChannel(GuildChannelUnion channel, Guild guild) {
        if (channel == null || !guild.getId().equals(channel.getGuild().getId())) {
            return false;
        }
        ChannelType type = channel.getType();
        return type == ChannelType.TEXT || type == ChannelType.NEWS;
    }

    private static ResolvedChannel resolveStoredChannel(Guild guild, String channelId) {
        GuildChannel channel = guild.getGuildChannelById(channelId);
        if (channel == null
                || (channel.getType() != ChannelType.TEXT && channel.getType() != ChannelType.NEWS)
                || !guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL)
                || !guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND)) {
            return new ResolvedChannel("Unavailable", " (deleted or inaccessible)");
        }
        return new ResolvedChannel(
                "#" + MarkdownSanitizer.escape(channel.getName()), "");
    }

    private void reply(
            InteractionHook hook, String message, String subcommand, String guildId) {
        try {
            hook.editOriginal(boundForDiscord(message))
                    .setAllowedMentions(Collections.emptyList())
                    .queue(
                            ignored -> { },
                            failure -> logFailure(subcommand, guildId, "reply", failure));
        } catch (RuntimeException failure) {
            logFailure(subcommand, guildId, "reply", failure);
        }
    }

    private void logFailure(
            String subcommand, String guildId, String operation, Throwable failure) {
        logger.warn(
                "Discord slash command operation failed: command={}, subcommand={}, guildId={}, "
                        + "operation={}, failureCategory={}",
                COMMAND_NAME,
                safeMetadata(subcommand),
                safeMetadata(guildId),
                operation,
                failureCategory(failure));
    }

    private static String safeMetadata(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String boundForDiscord(String message) {
        if (message.length() <= DISCORD_MESSAGE_LIMIT) {
            return message;
        }
        return message.substring(0, DISCORD_MESSAGE_LIMIT - 3) + "...";
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category.isBlank() ? "UnknownFailure" : category;
    }

    private record ResolvedChannel(String displayName, String warning) {
    }
}
