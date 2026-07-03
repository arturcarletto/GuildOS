package io.github.arturcarletto.guildos.discord;

import java.util.Collections;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.guildstatus.GuildStatusService;
import io.github.arturcarletto.guildos.guildstatus.GuildStatusView;

final class DiscordSlashCommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordSlashCommandListener.class);
    private static final String COMMAND_NAME = "status";
    private static final String UNAVAILABLE_MESSAGE =
            "Guild OS is not ready for this server. Please try again later.";
    private static final String FAILURE_MESSAGE =
            "Guild OS could not retrieve the status right now. Please try again later.";

    private final GuildStatusService statusService;

    DiscordSlashCommandListener(GuildStatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        if (guild == null) {
            return;
        }

        String guildId = guild.getId();
        try {
            event.deferReply(true).queue(
                    hook -> resolveAndReply(hook, guildId),
                    failure -> logFailure("defer", guildId, failure));
        }
        catch (RuntimeException failure) {
            logFailure("defer", guildId, failure);
        }
    }

    private void resolveAndReply(InteractionHook hook, String guildId) {
        String message;
        try {
            message = render(statusService.resolve(guildId));
        }
        catch (RuntimeException failure) {
            logFailure("resolve", guildId, failure);
            message = FAILURE_MESSAGE;
        }

        try {
            hook.editOriginal(message)
                    .setAllowedMentions(Collections.emptyList())
                    .queue(
                            ignored -> { },
                            failure -> logFailure("reply", guildId, failure));
        }
        catch (RuntimeException failure) {
            logFailure("reply", guildId, failure);
        }
    }

    private String render(GuildStatusView status) {
        return switch (status.state()) {
            case ACTIVE -> """
                    Guild OS status
                    Server: %s
                    Connection: Connected
                    Onboarding: Active
                    Timezone: %s
                    Locale: %s
                    Settings version: %d
                    """.formatted(
                            MarkdownSanitizer.escape(status.guildName()),
                            status.timezone(),
                            status.locale(),
                            status.settingsVersion()).stripTrailing();
            case NOT_ONBOARDED -> """
                    Guild OS status
                    Server: %s
                    Connection: Connected
                    Onboarding: Not onboarded

                    Guild OS is connected, but this server has not been onboarded yet.
                    An eligible server operator must sign in to Guild OS and complete onboarding.
                    """.formatted(MarkdownSanitizer.escape(status.guildName())).stripTrailing();
            case UNAVAILABLE -> UNAVAILABLE_MESSAGE;
        };
    }

    private void logFailure(String operation, String guildId, Throwable failure) {
        logger.warn(
                "Discord slash command operation failed: command={}, guildId={}, "
                        + "operation={}, failureCategory={}",
                COMMAND_NAME,
                guildId,
                operation,
                failureCategory(failure));
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category.isBlank() ? "UnknownFailure" : category;
    }
}
