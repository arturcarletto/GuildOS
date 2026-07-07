package io.github.arturcarletto.guildos.discord;

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
    void exposesTheTopLevelStatusCommand() {
        assertThat(catalog.commands()).hasSize(2);
        CommandData command = catalog.commands().get(0);

        assertThat(command.getName()).isEqualTo("status");
        assertThat(command.getDefaultPermissions()).isSameAs(DefaultMemberPermissions.ENABLED);
        assertThat(command).isInstanceOf(SlashCommandData.class);

        SlashCommandData slash = (SlashCommandData) command;
        assertThat(slash.getDescription()).isEqualTo("Show Guild OS status for this server");
        assertThat(slash.getOptions()).isEmpty();
        assertThat(slash.getSubcommandGroups()).isEmpty();
        assertThat(slash.getSubcommands()).isEmpty();
    }

    @Test
    void exposesTheCompleteWelcomeCommandContract() {
        CommandData command = catalog.commands().get(1);

        assertThat(command.getName()).isEqualTo("welcome");
        assertThat(command.getDefaultPermissions().getPermissionsRaw())
                .isEqualTo(Permission.MANAGE_SERVER.getRawValue());
        assertThat(command).isInstanceOf(SlashCommandData.class);

        SlashCommandData slash = (SlashCommandData) command;
        assertThat(slash.getDescription()).isEqualTo("Manage welcome messages");
        assertThat(slash.getOptions()).isEmpty();
        assertThat(slash.getSubcommandGroups()).isEmpty();
        assertThat(slash.getSubcommands()).extracting(SubcommandData::getName)
                .containsExactly("status", "configure", "preview", "disable");

        Map<String, SubcommandData> subcommands = slash.getSubcommands().stream()
                .collect(Collectors.toMap(SubcommandData::getName, Function.identity()));
        assertThat(subcommands.get("status").getDescription())
                .isEqualTo("Show the welcome configuration");
        assertThat(subcommands.get("preview").getDescription())
                .isEqualTo("Preview the configured welcome message");
        assertThat(subcommands.get("disable").getDescription())
                .isEqualTo("Disable welcome messages");
        assertThat(subcommands.get("status").getOptions()).isEmpty();
        assertThat(subcommands.get("preview").getOptions()).isEmpty();
        assertThat(subcommands.get("disable").getOptions()).isEmpty();

        SubcommandData configure = subcommands.get("configure");
        assertThat(configure.getDescription())
                .isEqualTo("Configure and enable welcome messages");
        assertThat(configure.getOptions()).hasSize(2);
        OptionData channel = configure.getOptions().get(0);
        assertThat(channel.getName()).isEqualTo("channel");
        assertThat(channel.getDescription())
                .isEqualTo("Channel that will receive welcome messages");
        assertThat(channel.getType()).isEqualTo(OptionType.CHANNEL);
        assertThat(channel.isRequired()).isTrue();
        assertThat(channel.getChannelTypes())
                .containsExactlyInAnyOrder(ChannelType.TEXT, ChannelType.NEWS);

        OptionData message = configure.getOptions().get(1);
        assertThat(message.getName()).isEqualTo("message");
        assertThat(message.getDescription()).isEqualTo("Welcome message template");
        assertThat(message.getType()).isEqualTo(OptionType.STRING);
        assertThat(message.isRequired()).isTrue();
        assertThat(message.getMinLength()).isEqualTo(1);
        assertThat(message.getMaxLength()).isEqualTo(1000);
    }
}
