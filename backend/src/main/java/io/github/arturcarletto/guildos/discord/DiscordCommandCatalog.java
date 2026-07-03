package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** Authoritative catalog of every Guild OS command registered for this Discord application. */
final class DiscordCommandCatalog {

    List<CommandData> commands() {
        CommandData status = Commands.slash(
                        "status",
                        "Show Guild OS status for this server"
                    )
                    .setDefaultPermissions(DefaultMemberPermissions.ENABLED);
        return List.of(status);
    }
}
