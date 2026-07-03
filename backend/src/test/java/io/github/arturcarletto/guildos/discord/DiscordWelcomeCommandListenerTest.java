package io.github.arturcarletto.guildos.discord;

import java.util.Collections;
import java.util.function.Consumer;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeService;
import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeState;
import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeView;
import io.github.arturcarletto.guildos.guildwelcome.GuildWelcomeConflictException;
import io.github.arturcarletto.guildos.guildwelcome.WelcomePreviewContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordWelcomeCommandListenerTest {

    private static final String GUILD_ID = "5001";

    private final GuildWelcomeService welcomeService = mock(GuildWelcomeService.class);
    private final DiscordWelcomeCommandListener listener =
            new DiscordWelcomeCommandListener(welcomeService);

    @Test
    void unrelatedRootCommandIsIgnored() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("status");

        listener.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(true);
        verifyNoInteractions(welcomeService);
    }

    @Test
    void directMessageIsRejectedAfterEphemeralDeferWithoutServiceWork() {
        InteractionHarness interaction = directMessage("status");

        listener.onSlashCommandInteraction(interaction.event());

        assertEphemeralSafeReply(interaction);
        assertThat(interaction.message()).contains("only be managed from a server");
        verifyNoInteractions(welcomeService);
    }

    @Test
    void missingMemberContextAndInsufficientPermissionAreRejected() {
        InteractionHarness missing = interaction("status", false);
        when(missing.event().getMember()).thenReturn(null);

        listener.onSlashCommandInteraction(missing.event());

        assertThat(missing.message()).contains("Manage Server");
        verifyNoInteractions(welcomeService);

        InteractionHarness denied = interaction("status", false);
        listener.onSlashCommandInteraction(denied.event());

        assertThat(denied.message()).contains("Manage Server");
        verifyNoInteractions(welcomeService);
    }

    @Test
    void effectiveManageServerPermissionAllowsEachRecognizedSubcommand() {
        for (String subcommand : new String[] {"status", "configure", "preview", "disable"}) {
            InteractionHarness interaction = interaction(subcommand, true);
            listener.onSlashCommandInteraction(interaction.event());
            verify(interaction.event()).deferReply(true);
            verify(interaction.editAction()).setAllowedMentions(Collections.emptyList());
        }
    }

    @Test
    void missingAndUnknownSubcommandsAreHandledSafely() {
        InteractionHarness missing = interaction(null, true);
        listener.onSlashCommandInteraction(missing.event());
        assertThat(missing.message()).isEqualTo("Unsupported welcome command.");

        InteractionHarness unknown = interaction("other", true);
        listener.onSlashCommandInteraction(unknown.event());
        assertThat(unknown.message()).isEqualTo("Unsupported welcome command.");
        verifyNoInteractions(welcomeService);
    }

    @Test
    void configureMapsAValidTextChannelAndTemplateToPlatformNeutralValues() {
        InteractionHarness interaction = configuredInteraction(ChannelType.TEXT, GUILD_ID);
        when(welcomeService.configure(GUILD_ID, "6001", "Welcome {member}"))
                .thenReturn(configured(true, "6001", "Welcome {member}", null, 0));

        listener.onSlashCommandInteraction(interaction.event());

        verify(welcomeService).configure(GUILD_ID, "6001", "Welcome {member}");
        assertThat(interaction.message())
                .contains("configured and enabled", "Channel: #welcome", "Version: 0")
                .doesNotContain("6001");
        assertEphemeralSafeReply(interaction);
    }

    @Test
    void configureAcceptsAnAnnouncementChannel() {
        InteractionHarness interaction = configuredInteraction(ChannelType.NEWS, GUILD_ID);
        when(welcomeService.configure(anyString(), anyString(), anyString()))
                .thenReturn(configured(true, "6001", "Welcome", null, 0));

        listener.onSlashCommandInteraction(interaction.event());

        verify(welcomeService).configure(GUILD_ID, "6001", "Welcome {member}");
    }

    @Test
    void configureRejectsUnsupportedAndCrossGuildChannelsBeforePersistence() {
        InteractionHarness voice = configuredInteraction(ChannelType.VOICE, GUILD_ID);
        listener.onSlashCommandInteraction(voice.event());
        assertThat(voice.message()).contains("text or announcement channel");

        InteractionHarness crossGuild = configuredInteraction(ChannelType.TEXT, "different-guild");
        listener.onSlashCommandInteraction(crossGuild.event());
        assertThat(crossGuild.message()).contains("text or announcement channel");

        verify(welcomeService, never()).configure(anyString(), anyString(), anyString());
    }

    @Test
    void configureRequiresBotViewAndSendPermissionsBeforePersistence() {
        InteractionHarness interaction = configuredInteraction(ChannelType.TEXT, GUILD_ID);
        when(interaction.selfMember().hasPermission(
                        interaction.selectedChannel(), Permission.MESSAGE_SEND))
                .thenReturn(false);

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message()).contains("View Channel and Send Messages");
        verify(welcomeService, never()).configure(anyString(), anyString(), anyString());
    }

    @Test
    void statusShowsSafeConfiguredDataAndHandlesDeletedChannel() {
        InteractionHarness interaction = interaction("status", true);
        when(welcomeService.status(GUILD_ID))
                .thenReturn(configured(
                        true,
                        "deleted-channel-id",
                        "Welcome {member} to {server}!",
                        null,
                        2));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .contains(
                        "Welcome configuration",
                        "Server: Heaven",
                        "Status: Enabled",
                        "Channel: Unavailable (deleted or inaccessible)",
                        "Template: Welcome {member} to {server}!",
                        "Version: 2")
                .doesNotContain("deleted-channel-id");
        assertEphemeralSafeReply(interaction);
    }

    @Test
    void previewUsesSafeLiveValuesAllowsDisabledStateAndNeverSendsToChannel() {
        InteractionHarness interaction = interaction("preview", true);
        when(interaction.member().getEffectiveName()).thenReturn("Artur");
        when(interaction.guild().getMemberCount()).thenReturn(42);
        when(welcomeService.preview(
                        eq(GUILD_ID),
                        eq(new WelcomePreviewContext("Artur", "Heaven", 42))))
                .thenReturn(configured(
                        false,
                        "deleted-channel-id",
                        "Welcome {member}",
                        "Welcome Artur to Heaven! You are member #42.",
                        3));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .contains(
                        "Welcome preview",
                        "Destination: Unavailable",
                        "deleted or inaccessible",
                        "Disabled (preview only)",
                        "Welcome Artur to Heaven! You are member #42.")
                .doesNotContain("deleted-channel-id");
        assertEphemeralSafeReply(interaction);
    }

    @Test
    void notConfiguredAndOnboardingResponsesAreControlled() {
        InteractionHarness status = interaction("status", true);
        when(welcomeService.status(GUILD_ID)).thenReturn(new GuildWelcomeView(
                GuildWelcomeState.NOT_CONFIGURED, "Heaven", false, null, null, null, 0));
        listener.onSlashCommandInteraction(status.event());
        assertThat(status.message()).contains("not configured", "/welcome configure");

        InteractionHarness disable = interaction("disable", true);
        when(welcomeService.disable(GUILD_ID)).thenReturn(new GuildWelcomeView(
                GuildWelcomeState.ONBOARDING_REQUIRED, "Heaven", false, null, null, null, 0));
        listener.onSlashCommandInteraction(disable.event());
        assertThat(disable.message()).contains("must be onboarded");
    }

    @Test
    void unexpectedFailureReturnsOnlyGenericSafeText() {
        InteractionHarness interaction = interaction("status", true);
        when(welcomeService.status(GUILD_ID))
                .thenThrow(new IllegalStateException("database password secret-template"));

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .isEqualTo("Guild OS could not manage welcome messages right now. Please try again later.")
                .doesNotContain("database", "password", "secret-template");
    }

    @Test
    void optimisticConflictReturnsOnlyTheControlledRetryMessage() {
        InteractionHarness interaction = interaction("disable", true);
        when(welcomeService.disable(GUILD_ID)).thenThrow(new GuildWelcomeConflictException());

        listener.onSlashCommandInteraction(interaction.event());

        assertThat(interaction.message())
                .isEqualTo(
                        "The welcome configuration changed while this command was running. Run the command again.");
    }

    private InteractionHarness configuredInteraction(ChannelType type, String channelGuildId) {
        InteractionHarness interaction = interaction("configure", true);
        OptionMapping channelOption = mock(OptionMapping.class);
        OptionMapping messageOption = mock(OptionMapping.class);
        GuildChannelUnion channel = interaction.selectedChannel();
        Guild channelGuild = mock(Guild.class);
        when(channelGuild.getId()).thenReturn(channelGuildId);
        when(channel.getGuild()).thenReturn(channelGuild);
        when(channel.getType()).thenReturn(type);
        when(channel.getId()).thenReturn("6001");
        when(channel.getName()).thenReturn("welcome");
        when(channelOption.getAsChannel()).thenReturn(channel);
        when(messageOption.getAsString()).thenReturn("Welcome {member}");
        when(interaction.event().getOption("channel")).thenReturn(channelOption);
        when(interaction.event().getOption("message")).thenReturn(messageOption);
        when(interaction.selfMember().hasPermission(channel, Permission.VIEW_CHANNEL)).thenReturn(true);
        when(interaction.selfMember().hasPermission(channel, Permission.MESSAGE_SEND)).thenReturn(true);
        return interaction;
    }

    @SuppressWarnings("unchecked")
    private InteractionHarness interaction(String subcommand, boolean allowed) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        SelfMember selfMember = mock(SelfMember.class);
        GuildChannelUnion selectedChannel = mock(GuildChannelUnion.class);
        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> editAction = mock(WebhookMessageEditAction.class);

        when(event.getName()).thenReturn("welcome");
        when(event.getSubcommandName()).thenReturn(subcommand);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(allowed);
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Heaven");
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(event.deferReply(true)).thenReturn(deferAction);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferAction).queue(any(), any());
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        when(editAction.setAllowedMentions(any())).thenReturn(editAction);

        return new InteractionHarness(
                event, guild, member, selfMember, selectedChannel, editAction, hook);
    }

    private InteractionHarness directMessage(String subcommand) {
        InteractionHarness interaction = interaction(subcommand, false);
        when(interaction.event().isFromGuild()).thenReturn(false);
        when(interaction.event().getGuild()).thenReturn(null);
        return interaction;
    }

    private static GuildWelcomeView configured(
            boolean enabled,
            String channelId,
            String template,
            String preview,
            long version) {
        return new GuildWelcomeView(
                GuildWelcomeState.CONFIGURED,
                "Heaven",
                enabled,
                channelId,
                template,
                preview,
                version);
    }

    private static void assertEphemeralSafeReply(InteractionHarness interaction) {
        verify(interaction.event()).deferReply(true);
        verify(interaction.editAction()).setAllowedMentions(Collections.emptyList());
        verify(interaction.event(), never()).reply(anyString());
    }

    private record InteractionHarness(
            SlashCommandInteractionEvent event,
            Guild guild,
            Member member,
            SelfMember selfMember,
            GuildChannelUnion selectedChannel,
            WebhookMessageEditAction<Message> editAction,
            InteractionHook hook) {

        String message() {
            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            verify(hook).editOriginal(message.capture());
            return message.getValue();
        }
    }
}
