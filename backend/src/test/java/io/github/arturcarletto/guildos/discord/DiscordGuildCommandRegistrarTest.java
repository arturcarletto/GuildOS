package io.github.arturcarletto.guildos.discord;

import java.util.List;
import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordGuildCommandRegistrarTest {

    private final DiscordCommandCatalog catalog = mock(DiscordCommandCatalog.class);
    private final DiscordGuildCommandRegistrar registrar = new DiscordGuildCommandRegistrar(catalog);

    @Test
    void bulkUpdatesTheCompleteCatalogAsynchronously() {
        Guild guild = mock(Guild.class);
        CommandListUpdateAction action = mock(CommandListUpdateAction.class);
        List<CommandData> commands = List.of(mock(CommandData.class));
        when(guild.getId()).thenReturn("4001");
        when(guild.updateCommands()).thenReturn(action);
        when(catalog.commands()).thenReturn(commands);
        when(action.addCommands(commands)).thenReturn(action);

        registrar.reconcile(guild);

        verify(action).addCommands(commands);
        verify(action).queue(any(), any());
    }

    @Test
    void asynchronousRegistrationFailureDoesNotEscape() {
        Guild guild = mock(Guild.class);
        CommandListUpdateAction action = mock(CommandListUpdateAction.class);
        List<CommandData> commands = List.of(mock(CommandData.class));
        when(guild.getId()).thenReturn("4002");
        when(guild.updateCommands()).thenReturn(action);
        when(catalog.commands()).thenReturn(commands);
        when(action.addCommands(commands)).thenReturn(action);
        doAnswer(invocation -> {
            Consumer<Throwable> failure = invocation.getArgument(1);
            failure.accept(new IllegalStateException("sensitive upstream detail"));
            return null;
        }).when(action).queue(any(), any());

        assertThatCode(() -> registrar.reconcile(guild)).doesNotThrowAnyException();
    }

    @Test
    void synchronousRegistrationFailureDoesNotEscape() {
        Guild guild = mock(Guild.class);
        when(guild.getId()).thenReturn("4003");
        when(guild.updateCommands()).thenThrow(new IllegalStateException("sensitive upstream detail"));

        assertThatCode(() -> registrar.reconcile(guild)).doesNotThrowAnyException();
    }
}
