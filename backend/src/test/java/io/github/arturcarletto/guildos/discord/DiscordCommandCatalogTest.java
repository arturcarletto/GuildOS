package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordCommandCatalogTest {

    private final DiscordCommandCatalog catalog = new DiscordCommandCatalog();

    @Test
    void exposesTheCompleteGuildOnlyStatusCommandCatalog() {
        assertThat(catalog.commands()).hasSize(1);
        CommandData command = catalog.commands().get(0);

        assertThat(command.getName()).isEqualTo("guildos");
        assertThat(command.getDefaultPermissions()).isSameAs(DefaultMemberPermissions.ENABLED);
        assertThat(command).isInstanceOf(SlashCommandData.class);

        SlashCommandData slash = (SlashCommandData) command;
        assertThat(slash.getOptions()).isEmpty();
        assertThat(slash.getSubcommandGroups()).isEmpty();
        assertThat(slash.getSubcommands()).singleElement().satisfies(status -> {
            assertThat(status.getName()).isEqualTo("status");
            assertThat(status.getDescription()).isEqualTo("Show Guild OS status for this server");
            assertThat(status.getOptions()).isEmpty();
        });
    }
}
