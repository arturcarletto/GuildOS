package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.function.Consumer;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageConflictException;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageView;
import io.github.arturcarletto.guildos.guildmembermessage.InvalidMemberMessageConfigurationException;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageAppearance;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageState;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

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

class DiscordMemberMessageCommandListenerTest {

    private static final String GUILD_ID = "5001";

    private final GuildMemberMessageService service = mock(GuildMemberMessageService.class);
    private final DiscordMemberMessageCommandListener listener = new DiscordMemberMessageCommandListener(
            service,
            new DiscordMemberMessageEmbedFactory(),
            new DiscordMemberMessageChannelResolver(),
            Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void unrelatedCommandIsIgnored() {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        when(event.getName()).thenReturn("status");

        listener.onSlashCommandInteraction(event);

        verify(event, never()).deferReply(true);
        verifyNoInteractions(service);
    }

    @Test
    void directMessageIsRejectedAfterEphemeralDeferWithoutServiceWork() {
        Harness harness = harness("welcome", "status", true);
        when(harness.event.isFromGuild()).thenReturn(false);
        when(harness.event.getGuild()).thenReturn(null);

        listener.onSlashCommandInteraction(harness.event);

        verify(harness.event).deferReply(true);
        assertThat(embed(harness).getDescription()).contains("only be managed from a server");
        verifyNoInteractions(service);
    }

    @Test
    void insufficientPermissionIsRejected() {
        Harness harness = harness("welcome", "status", false);

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getDescription()).contains("Manage Server");
        verifyNoInteractions(service);
    }

    @Test
    void statusRepliesWithAnEphemeralEmbedThatHidesTheChannelIdAndVersion() {
        Harness harness = harness("welcome", "status", true);
        when(service.status(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(configured(MemberMessageKind.WELCOME, true, "deleted-channel-id"));

        listener.onSlashCommandInteraction(harness.event);

        MessageEmbed embed = embed(harness);
        assertThat(embed.getTitle()).isEqualTo("Welcome configuration");
        assertThat(embed.toData().toString())
                .doesNotContain("deleted-channel-id")
                .doesNotContain("version", "Version");
        verify(harness.event).deferReply(true);
        verify(harness.editAction).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void configureAcceptsAValidTextChannelAndRepliesWithASuccessEmbed() {
        Harness harness = configureHarness("welcome");
        when(service.configure(eq(GUILD_ID), eq(MemberMessageKind.WELCOME), any()))
                .thenReturn(configured(MemberMessageKind.WELCOME, true, "6001"));

        listener.onSlashCommandInteraction(harness.event);

        verify(service).configure(eq(GUILD_ID), eq(MemberMessageKind.WELCOME), any());
        assertThat(embed(harness).getTitle()).contains("configured");
        verify(harness.editAction).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void goodbyeCommandRoutesToTheGoodbyeKind() {
        Harness harness = harness("goodbye", "status", true);
        when(service.status(GUILD_ID, MemberMessageKind.GOODBYE))
                .thenReturn(configured(MemberMessageKind.GOODBYE, true, "6001"));

        listener.onSlashCommandInteraction(harness.event);

        verify(service).status(GUILD_ID, MemberMessageKind.GOODBYE);
        assertThat(embed(harness).getTitle()).isEqualTo("Goodbye configuration");
    }

    @Test
    void toggleWithoutConfigurationWarnsToConfigureFirst() {
        Harness harness = harness("welcome", "toggle", true);
        when(service.toggle(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(new GuildMemberMessageView(MemberMessageState.NOT_CONFIGURED, "Heaven", MemberMessageKind.WELCOME, false, null, null, null));

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getDescription()).contains("Configure this feature first");
    }

    @Test
    void toggleReportsTheNewState() {
        Harness harness = harness("welcome", "toggle", true);
        when(service.toggle(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(configured(MemberMessageKind.WELCOME, false, "6001"));

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getTitle()).contains("disabled");
    }

    @Test
    void previewEditsWithTheRealMessageStructureAndNeverSendsToAChannel() {
        Harness harness = harness("welcome", "preview", true);
        GuildMemberMessageView view = new GuildMemberMessageView(
                MemberMessageState.CONFIGURED, "Heaven", MemberMessageKind.WELCOME, true, "6001",
                appearance(MemberMessageKind.WELCOME),
                new RenderedMemberMessage(MemberMessageKind.WELCOME, "Welcome", "Hi Artur", 0x57F287,
                        null, "Footer", 42, true, "<@100>", null, null));
        when(service.preview(eq(GUILD_ID), eq(MemberMessageKind.WELCOME), any())).thenReturn(view);

        listener.onSlashCommandInteraction(harness.event);

        ArgumentCaptor<MessageEditData> captor = ArgumentCaptor.forClass(MessageEditData.class);
        verify(harness.hook).editOriginal(captor.capture());
        assertThat(captor.getValue().getEmbeds().get(0).getTitle()).isEqualTo("Welcome");
        verify(harness.editAction).setAllowedMentions(Collections.emptyList());
    }

    @Test
    void notConfiguredStatusRepliesWithInfoEmbed() {
        Harness harness = harness("welcome", "status", true);
        when(service.status(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(new GuildMemberMessageView(MemberMessageState.NOT_CONFIGURED, "Heaven", MemberMessageKind.WELCOME, false, null, null, null));

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getDescription()).contains("not configured yet");
    }

    @Test
    void invalidConfigurationRepliesWithAHelpfulErrorEmbed() {
        Harness harness = configureHarness("welcome");
        when(service.configure(eq(GUILD_ID), eq(MemberMessageKind.WELCOME), any()))
                .thenThrow(new InvalidMemberMessageConfigurationException("The color must be a hexadecimal value"));

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getDescription()).contains("hexadecimal");
    }

    @Test
    void concurrentConflictRepliesWithARetryWarning() {
        Harness harness = harness("welcome", "toggle", true);
        when(service.toggle(GUILD_ID, MemberMessageKind.WELCOME))
                .thenThrow(new GuildMemberMessageConflictException());

        listener.onSlashCommandInteraction(harness.event);

        assertThat(embed(harness).getDescription()).contains("changed while this command was running");
    }

    // ----- harness -----

    private Harness configureHarness(String command) {
        Harness harness = harness(command, "configure", true);
        OptionMapping channelOption = mock(OptionMapping.class);
        OptionMapping messageOption = mock(OptionMapping.class);
        GuildChannelUnion channel = mock(GuildChannelUnion.class);
        Guild channelGuild = mock(Guild.class);
        when(channelGuild.getId()).thenReturn(GUILD_ID);
        when(channel.getGuild()).thenReturn(channelGuild);
        when(channel.getType()).thenReturn(ChannelType.TEXT);
        when(channel.getId()).thenReturn("6001");
        when(channel.getName()).thenReturn("welcome");
        when(channelOption.getAsChannel()).thenReturn(channel);
        when(messageOption.getAsString()).thenReturn("Hi {member}");
        when(harness.event.getOption("channel")).thenReturn(channelOption);
        when(harness.event.getOption("message")).thenReturn(messageOption);
        when(harness.selfMember.hasPermission(any(GuildChannel.class), any(Permission[].class)))
                .thenReturn(true);
        return harness;
    }

    @SuppressWarnings("unchecked")
    private Harness harness(String command, String subcommand, boolean allowed) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        SelfMember selfMember = mock(SelfMember.class);
        ReplyCallbackAction deferAction = mock(ReplyCallbackAction.class);
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> editAction = mock(WebhookMessageEditAction.class);

        when(event.getName()).thenReturn(command);
        when(event.getSubcommandName()).thenReturn(subcommand);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(allowed);
        when(member.getUser()).thenReturn(user);
        when(user.getName()).thenReturn("artur");
        when(member.getEffectiveName()).thenReturn("Artur");
        when(member.getAsMention()).thenReturn("<@100>");
        when(member.getEffectiveAvatarUrl()).thenReturn("https://cdn/a.png");
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Heaven");
        when(guild.getMemberCount()).thenReturn(42);
        when(guild.getSelfMember()).thenReturn(selfMember);
        when(guild.getGuildChannelById(anyString())).thenReturn(null);
        when(event.deferReply(true)).thenReturn(deferAction);
        doAnswer(invocation -> {
            Consumer<InteractionHook> success = invocation.getArgument(0);
            success.accept(hook);
            return null;
        }).when(deferAction).queue(any(), any());
        when(hook.editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(editAction);
        when(hook.editOriginal(any(MessageEditData.class))).thenReturn(editAction);
        when(editAction.setContent(any())).thenReturn(editAction);
        when(editAction.setAllowedMentions(any())).thenReturn(editAction);

        return new Harness(event, guild, member, selfMember, hook, editAction);
    }

    private static MessageEmbed embed(Harness harness) {
        ArgumentCaptor<MessageEmbed> captor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(harness.hook).editOriginalEmbeds(captor.capture());
        return captor.getValue();
    }

    private static GuildMemberMessageView configured(MemberMessageKind kind, boolean enabled, String channelId) {
        return new GuildMemberMessageView(
                MemberMessageState.CONFIGURED, "Heaven", kind, enabled, channelId,
                appearance(kind), null);
    }

    private static MemberMessageAppearance appearance(MemberMessageKind kind) {
        boolean welcome = kind == MemberMessageKind.WELCOME;
        return new MemberMessageAppearance(
                (welcome ? "Welcome to {server}!" : "A member has left"),
                "Hi {member}", welcome ? 0x57F287 : 0xED4245, null,
                (welcome ? "Welcome • {server}" : "Goodbye • {server}"),
                welcome, false, null, null);
    }

    private record Harness(
            SlashCommandInteractionEvent event,
            Guild guild,
            Member member,
            SelfMember selfMember,
            InteractionHook hook,
            WebhookMessageEditAction<Message> editAction) {
    }
}
