package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.DeliveryOutcome;
import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.DeliveryTarget;
import io.github.arturcarletto.guildos.discord.DiscordMemberMessageDeliveryMetrics.Outcome;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageAppearance;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageDeliveryPlan;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordMemberLifecycleListenerTest {

    private static final String GUILD_ID = "5001";

    private final GuildMemberMessageService service = mock(GuildMemberMessageService.class);
    private final DiscordMemberMessageChannelResolver channelResolver =
            mock(DiscordMemberMessageChannelResolver.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DiscordMemberLifecycleListener listener = new DiscordMemberLifecycleListener(
            service,
            new DiscordMemberMessageEmbedFactory(),
            channelResolver,
            new DiscordMemberMessageDeliveryMetrics(registry),
            Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void enabledWelcomeSendsExactlyOneMessageMentioningOnlyTheJoiningUser() {
        Join join = join(false);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(deliver(MemberMessageKind.WELCOME, true));
        when(service.render(eq(MemberMessageKind.WELCOME), any(), any()))
                .thenReturn(rendered(MemberMessageKind.WELCOME, true, "<@100>"));
        TextChannel channel = readyChannel();

        listener.onGuildMemberJoin(join.event);

        MessageCreateData sent = capturedMessage(channel);
        assertThat(sent.getMentionedUsers()).containsExactly("100");
        assertThat(counter("welcome", "sent")).isEqualTo(1.0);
    }

    @Test
    void enabledGoodbyeSendsAMessageThatMentionsNobody() {
        Remove remove = remove(false);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.GOODBYE))
                .thenReturn(deliver(MemberMessageKind.GOODBYE, true));
        when(service.render(eq(MemberMessageKind.GOODBYE), any(), any()))
                .thenReturn(rendered(MemberMessageKind.GOODBYE, false, ""));
        TextChannel channel = readyChannel();

        listener.onGuildMemberRemove(remove.event);

        MessageCreateData sent = capturedMessage(channel);
        assertThat(sent.getMentionedUsers()).isEmpty();
        assertThat(counter("goodbye", "sent")).isEqualTo(1.0);
    }

    @Test
    void disabledConfigurationSendsNothing() {
        Join join = join(false);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(new MemberMessageDeliveryPlan(
                        MemberMessageDeliveryPlan.Decision.DISABLED, MemberMessageKind.WELCOME, null, null));

        listener.onGuildMemberJoin(join.event);

        verify(channelResolver, never()).resolveDelivery(any(), any());
        assertThat(counter("welcome", "disabled")).isEqualTo(1.0);
    }

    @Test
    void missingConfigurationSendsNothing() {
        Join join = join(false);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(new MemberMessageDeliveryPlan(
                        MemberMessageDeliveryPlan.Decision.NOT_CONFIGURED, MemberMessageKind.WELCOME, null, null));

        listener.onGuildMemberJoin(join.event);

        assertThat(counter("welcome", "not_configured")).isEqualTo(1.0);
    }

    @Test
    void botsAreIgnoredUnlessIncludedButDeliveredWhenIncluded() {
        Join botJoin = join(true);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(deliver(MemberMessageKind.WELCOME, false));

        listener.onGuildMemberJoin(botJoin.event);

        verify(channelResolver, never()).resolveDelivery(any(), any());
        assertThat(counter("welcome", "bot_ignored")).isEqualTo(1.0);

        Join includedBot = join(true);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(deliver(MemberMessageKind.WELCOME, true));
        when(service.render(eq(MemberMessageKind.WELCOME), any(), any()))
                .thenReturn(rendered(MemberMessageKind.WELCOME, true, "<@100>"));
        readyChannel();

        listener.onGuildMemberJoin(includedBot.event);
        assertThat(counter("welcome", "sent")).isEqualTo(1.0);
    }

    @Test
    void missingChannelAndMissingPermissionsSkipDeliverySafely() {
        Join channelGone = join(false);
        when(service.resolveDelivery(GUILD_ID, MemberMessageKind.WELCOME))
                .thenReturn(deliver(MemberMessageKind.WELCOME, true));
        when(channelResolver.resolveDelivery(any(), anyString()))
                .thenReturn(new DeliveryTarget(null, DeliveryOutcome.CHANNEL_UNAVAILABLE));

        listener.onGuildMemberJoin(channelGone.event);
        assertThat(counter("welcome", "channel_unavailable")).isEqualTo(1.0);

        Join noPerms = join(false);
        when(channelResolver.resolveDelivery(any(), anyString()))
                .thenReturn(new DeliveryTarget(null, DeliveryOutcome.PERMISSION_DENIED));
        when(service.render(eq(MemberMessageKind.WELCOME), any(), any()))
                .thenReturn(rendered(MemberMessageKind.WELCOME, true, "<@100>"));

        listener.onGuildMemberJoin(noPerms.event);
        assertThat(counter("welcome", "permission_denied")).isEqualTo(1.0);
    }

    // ----- helpers -----

    private TextChannel readyChannel() {
        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn("6001");
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(any(MessageCreateData.class))).thenReturn(action);
        doAnswer(invocation -> {
            Consumer<Object> success = invocation.getArgument(0);
            success.accept(null);
            return null;
        }).when(action).queue(any(), any());
        when(channelResolver.resolveDelivery(any(), anyString()))
                .thenReturn(new DeliveryTarget(channel, DeliveryOutcome.READY));
        return channel;
    }

    private static MessageCreateData capturedMessage(TextChannel channel) {
        ArgumentCaptor<MessageCreateData> captor = ArgumentCaptor.forClass(MessageCreateData.class);
        verify(channel).sendMessage(captor.capture());
        return captor.getValue();
    }

    private double counter(String kind, String outcome) {
        return registry.find(DiscordMemberMessageDeliveryMetrics.METRIC)
                .tag("kind", kind).tag("outcome", outcome).counter().count();
    }

    private static MemberMessageDeliveryPlan deliver(MemberMessageKind kind, boolean includeBots) {
        boolean welcome = kind == MemberMessageKind.WELCOME;
        MemberMessageAppearance appearance = new MemberMessageAppearance(
                "Title", "Body {member}", welcome ? 0x57F287 : 0xED4245, null, "Footer",
                welcome, includeBots, null, null);
        return new MemberMessageDeliveryPlan(
                MemberMessageDeliveryPlan.Decision.DELIVER, kind, "6001", appearance);
    }

    private static RenderedMemberMessage rendered(
            MemberMessageKind kind, boolean mention, String mentionText) {
        return new RenderedMemberMessage(
                kind, "Title", "Body Artur", 0x57F287, null, "Footer", 42, mention, mentionText, null, null);
    }

    private Join join(boolean bot) {
        GuildMemberJoinEvent event = mock(GuildMemberJoinEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        User user = mock(User.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(member.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(bot);
        when(user.getId()).thenReturn("100");
        when(user.getName()).thenReturn("artur");
        when(member.getEffectiveName()).thenReturn("Artur");
        when(member.getEffectiveAvatarUrl()).thenReturn("https://cdn/a.png");
        when(member.getAsMention()).thenReturn("<@100>");
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Heaven");
        when(guild.getMemberCount()).thenReturn(42);
        return new Join(event);
    }

    private Remove remove(boolean bot) {
        GuildMemberRemoveEvent event = mock(GuildMemberRemoveEvent.class);
        Guild guild = mock(Guild.class);
        User user = mock(User.class);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);
        when(user.isBot()).thenReturn(bot);
        when(user.getId()).thenReturn("100");
        when(user.getName()).thenReturn("artur");
        when(user.getEffectiveName()).thenReturn("Artur");
        when(user.getEffectiveAvatarUrl()).thenReturn("https://cdn/a.png");
        when(guild.getId()).thenReturn(GUILD_ID);
        when(guild.getName()).thenReturn("Heaven");
        when(guild.getMemberCount()).thenReturn(41);
        return new Remove(event);
    }

    private record Join(GuildMemberJoinEvent event) {
    }

    private record Remove(GuildMemberRemoveEvent event) {
    }
}
