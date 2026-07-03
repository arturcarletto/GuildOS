package io.github.arturcarletto.guildos.discord;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordCommandCatalogTest {

    private final DiscordCommandCatalog catalog = new DiscordCommandCatalog();

    @Test
    void exposesExactlyStatusWelcomeAndGoodbye() {
        assertThat(catalog.commands())
                .extracting(CommandData::getName)
                .containsExactly("status", "welcome", "goodbye");
    }

    @Test
    void exposesTheTopLevelStatusCommand() {
        CommandData command = catalog.commands().get(0);
        assertThat(command.getName()).isEqualTo("status");
        assertThat(command.getDefaultPermissions()).isSameAs(DefaultMemberPermissions.ENABLED);

        SlashCommandData slash = (SlashCommandData) command;
        assertThat(slash.getDescription()).isEqualTo("Show Guild OS status for this server");
        assertThat(slash.getSubcommands()).isEmpty();
        assertThat(slash.getOptions()).isEmpty();
    }

    @Test
    void welcomeExposesStatusConfigurePreviewToggleWithoutDisable() {
        SlashCommandData welcome = memberMessageCommand("welcome");

        assertThat(welcome.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(Permission.MANAGE_SERVER.getRawValue());
        assertThat(welcome.getDescription()).isEqualTo("Manage welcome messages");
        assertThat(welcome.getSubcommands()).extracting(SubcommandData::getName)
                .containsExactly("status", "configure", "preview", "toggle")
                .doesNotContain("disable");
    }

    @Test
    void goodbyeMirrorsTheWelcomeContract() {
        SlashCommandData goodbye = memberMessageCommand("goodbye");

        assertThat(goodbye.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(Permission.MANAGE_SERVER.getRawValue());
        assertThat(goodbye.getDescription()).isEqualTo("Manage goodbye messages");
        assertThat(goodbye.getSubcommands()).extracting(SubcommandData::getName)
                .containsExactly("status", "configure", "preview", "toggle");
    }

    @Test
    void welcomeConfigureRequiresChannelAndMessageAndOffersWelcomeOnlyOptions() {
        SubcommandData configure = subcommands(memberMessageCommand("welcome")).get("configure");
        Map<String, OptionData> options = configure.getOptions().stream()
                .collect(Collectors.toMap(OptionData::getName, Function.identity()));

        assertThat(options.keySet()).containsExactlyInAnyOrder(
                "channel", "message", "title", "color", "image", "footer",
                "include-bots", "mention-member", "button-label", "button-url");

        OptionData channel = options.get("channel");
        assertThat(channel.getType()).isEqualTo(OptionType.CHANNEL);
        assertThat(channel.isRequired()).isTrue();
        assertThat(channel.getChannelTypes())
                .containsExactlyInAnyOrder(ChannelType.TEXT, ChannelType.NEWS);

        OptionData message = options.get("message");
        assertThat(message.getType()).isEqualTo(OptionType.STRING);
        assertThat(message.isRequired()).isTrue();
        assertThat(message.getMinLength()).isEqualTo(1);
        assertThat(message.getMaxLength()).isEqualTo(1000);

        assertThat(options.get("title").isRequired()).isFalse();
        assertThat(options.get("mention-member").getType()).isEqualTo(OptionType.BOOLEAN);
        assertThat(options.get("include-bots").getType()).isEqualTo(OptionType.BOOLEAN);
        assertThat(options.get("button-url").isRequired()).isFalse();
    }

    @Test
    void goodbyeConfigureOmitsMentionAndButtonOptions() {
        SubcommandData configure = subcommands(memberMessageCommand("goodbye")).get("configure");

        assertThat(configure.getOptions()).extracting(OptionData::getName)
                .containsExactlyInAnyOrder(
                        "channel", "message", "title", "color", "image", "footer", "include-bots")
                .doesNotContain("mention-member", "button-label", "button-url");
    }

    private SlashCommandData memberMessageCommand(String name) {
        return (SlashCommandData) catalog.commands().stream()
                .filter(command -> command.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, SubcommandData> subcommands(SlashCommandData command) {
        List<SubcommandData> subcommands = command.getSubcommands();
        return subcommands.stream()
                .collect(Collectors.toMap(SubcommandData::getName, Function.identity()));
    }
}
