package io.github.arturcarletto.guildos.discord;

import java.util.ArrayList;
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
        CommandData status = Commands.slash("status", "Show Guild OS status for this server")
                .setDefaultPermissions(DefaultMemberPermissions.ENABLED);
        return List.of(status, memberMessageCommand("welcome"), memberMessageCommand("goodbye"));
    }

    private CommandData memberMessageCommand(String name) {
        boolean welcome = "welcome".equals(name);
        String noun = welcome ? "welcome" : "goodbye";
        return Commands.slash(name, "Manage " + noun + " messages")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("status", "Show the " + noun + " configuration"),
                        configureSubcommand(noun, welcome),
                        new SubcommandData("preview", "Preview the " + noun + " message"),
                        new SubcommandData("toggle", "Enable or disable " + noun + " messages"));
    }

    private SubcommandData configureSubcommand(String noun, boolean welcome) {
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(
                        OptionType.CHANNEL, "channel", "Channel that will receive " + noun + " messages", true)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS));
        options.add(new OptionData(
                        OptionType.STRING, "message", "Message shown as the embed description", true)
                .setRequiredLength(1, 1000));
        options.add(new OptionData(OptionType.STRING, "title", "Embed title (optional)", false)
                .setRequiredLength(1, 256));
        options.add(new OptionData(
                OptionType.STRING, "color", "Accent color as a hex value like #57F287 (optional)", false)
                .setRequiredLength(1, 20));
        options.add(new OptionData(
                OptionType.STRING, "image", "Banner image HTTPS URL (optional)", false)
                .setRequiredLength(1, 512));
        options.add(new OptionData(OptionType.STRING, "footer", "Embed footer (optional)", false)
                .setRequiredLength(1, 256));
        options.add(new OptionData(
                OptionType.BOOLEAN, "include-bots", "Also announce bot accounts (optional)", false));
        if (welcome) {
            options.add(new OptionData(
                    OptionType.BOOLEAN, "mention-member", "Mention the joining member (optional)", false));
            options.add(new OptionData(
                    OptionType.STRING, "button-label", "Link button label (optional)", false)
                    .setRequiredLength(1, 80));
            options.add(new OptionData(
                    OptionType.STRING, "button-url", "Link button HTTPS URL (optional)", false)
                    .setRequiredLength(1, 512));
        }
        return new SubcommandData("configure", "Configure " + noun + " messages").addOptions(options);
    }
}
