package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconciles the complete authoritative Guild OS command catalog for one guild. */
final class DiscordGuildCommandRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGuildCommandRegistrar.class);

    private final DiscordCommandCatalog commandCatalog;

    DiscordGuildCommandRegistrar(DiscordCommandCatalog commandCatalog) {
        this.commandCatalog = commandCatalog;
    }

    void reconcile(Guild guild) {
        String guildId = guild.getId();
        try {
            // Guild bulk update replaces this application's guild command list in one operation.
            guild.updateCommands()
                    .addCommands(commandCatalog.commands())
                    .queue(
                            ignored -> logger.info(
                                    "Discord guild commands reconciled: guildId={}", guildId),
                            failure -> logFailure(guildId, failure));
        }
        catch (RuntimeException failure) {
            logFailure(guildId, failure);
        }
    }

    private void logFailure(String guildId, Throwable failure) {
        logger.warn(
                "Discord guild command reconciliation failed: guildId={}, failureCategory={}",
                guildId,
                failureCategory(failure));
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category.isBlank() ? "UnknownFailure" : category;
    }
}
