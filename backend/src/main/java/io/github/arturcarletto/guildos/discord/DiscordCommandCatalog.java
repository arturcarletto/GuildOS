package io.github.arturcarletto.guildos.discord;

import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/** Authoritative catalog of every Guild OS command registered for this Discord application. */
final class DiscordCommandCatalog {

    List<CommandData> commands() {
        CommandData status = Commands.slash(
                        "status",
                        "Show Guild OS status for this server")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED);
        CommandData welcome = Commands.slash("welcome", "Manage welcome messages")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("status", "Show the welcome configuration"),
                        new SubcommandData("configure", "Configure and enable welcome messages")
                                .addOptions(
                                        new OptionData(
                                                        OptionType.CHANNEL,
                                                        "channel",
                                                        "Channel that will receive welcome messages",
                                                        true)
                                                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                                        new OptionData(
                                                        OptionType.STRING,
                                                        "message",
                                                        "Welcome message template",
                                                        true)
                                                .setRequiredLength(1, 1000)),
                        new SubcommandData("preview", "Preview the configured welcome message"),
                        new SubcommandData("disable", "Disable welcome messages"));
        return List.of(status, welcome);
    }
}
