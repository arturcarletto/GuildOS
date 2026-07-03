package io.github.arturcarletto.guildos.discord;

import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.guildstatus.GuildStatusService;
import io.github.arturcarletto.guildos.guildstatus.GuildStatusView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordSlashCommandListenerTest {

    private static final String GUILD_ID = "5001";

    private final GuildStatusService statusService = mock(GuildStatusService.class);
    private final DiscordSlashCommandListener listener = new DiscordSlashCommandListener(statusService);

    @Test
    void handlesStatusWithAnEphemeralDeferredActiveResponse() {
        InteractionHarness interaction = interaction();
        when(statusService.resolve(GUILD_ID)).thenReturn(GuildStatusView.active(
                "Heaven", "America/Sao_Paulo", "pt-BR", 1));

        listener.onSlashCommandInteraction(interaction.event());

        verify(interaction.event()).deferReply(true);
        String message = interaction.message();
        assertThat(message)
                .contains(
                        "Guild OS status",
                        "Server: Heaven",
                        "Connection: Connected",
                        "Onboarding: Active",
                        "Timezone: America/Sao_Paulo",
                        "Locale: pt-BR",
                        "Settings version: 1")
                .doesNotContain(
                        "operatorId",
                        "registeredGuildId",
                        "accessToken",
                        "clientSecret",
                        "sessionId");
        verify(interaction.editAction()).setAllowedMentions(any());
    }

    @Test
    void activeResponseUsesDocumentedDefaultsWhenSettingsAreMissing() {
        InteractionHarness interaction = interaction();
        when(statusService.resolve(GUILD_ID)).thenReturn(
                GuildStatusView.active("Default Guild", "UTC", "en-US", 0));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .contains("Timezone: UTC", "Locale: en-US", "Settings version: 0");
    }

    @Test
    void nonOnboardedResponseExplainsTheRequiredActionWithoutOperatorDetails() {
        InteractionHarness interaction = interaction();
        when(statusService.resolve(GUILD_ID)).thenReturn(GuildStatusView.notOnboarded("New Guild"));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .contains(
                        "Server: New Guild",
                        "Connection: Connected",
                        "Onboarding: Not onboarded",
                        "eligible server operator",
                        "complete onboarding")
                .doesNotContain("operatorId", "OWNER", "ADMIN", "permissions");
    }

    @Test
    void unavailableGuildReceivesASafeResponse() {
        InteractionHarness interaction = interaction();
        when(statusService.resolve(GUILD_ID)).thenReturn(GuildStatusView.unavailable());

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .isEqualTo("Guild OS is not ready for this server. Please try again later.");
    }

    @Test
    void unexpectedResolutionFailureReceivesOnlyTheGenericError() {
        InteractionHarness interaction = interaction();
        when(statusService.resolve(GUILD_ID))
                .thenThrow(new IllegalStateException("database password and internal UUID"));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .isEqualTo("Guild OS could not retrieve the status right now. Please try again later.")
                .doesNotContain("database", "password", "UUID");
    }

    @Test
    void unrelatedCommandIsIgnored() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("other");

        listener.onSlashCommandInteraction(event);

        verify(statusService, never()).resolve(anyString());
        verify(event, never()).deferReply(true);
    }

    @Test
    void missingOrDifferentSubcommandDoesNotTriggerStatus() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("guildos");
        when(event.getSubcommandName()).thenReturn("other");

        listener.onSlashCommandInteraction(event);

        verify(statusService, never()).resolve(anyString());
        verify(event, never()).deferReply(true);
    }

    @Test
    void directMessageDoesNotTriggerStatus() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("guildos");
        when(event.getSubcommandName()).thenReturn("status");
        when(event.isFromGuild()).thenReturn(false);

        listener.onSlashCommandInteraction(event);

        verify(statusService, never()).resolve(anyString());
        verify(event, never()).deferReply(true);
    }

    @SuppressWarnings("unchecked")
    private InteractionHarness interaction() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> editAction = mock(WebhookMessageEditAction.class);

        when(event.getName()).thenReturn("guildos");
        when(event.getSubcommandName()).thenReturn("status");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(event.deferReply(true)).thenReturn(deferAction);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferAction).queue(any(), any());
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(editAction.setAllowedMentions(any())).thenReturn(editAction);

        return new InteractionHarness(event, editAction, hook);
    }

    private record InteractionHarness(
            SlashCommandInteractionEvent event,
            WebhookMessageEditAction<Message> editAction,
            InteractionHook hook) {

        String message() {
            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            verify(hook).editOriginal(message.capture());
            return message.getValue();
        }
    }
}
