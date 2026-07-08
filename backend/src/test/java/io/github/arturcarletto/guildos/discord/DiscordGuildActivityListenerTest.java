package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.arturcarletto.guildos.guildactivity.GuildActivityEventType;
import io.github.arturcarletto.guildos.guildactivity.GuildActivityIngestionResult;
import io.github.arturcarletto.guildos.guildactivity.GuildActivityIngestionService;
import io.github.arturcarletto.guildos.guildactivity.IngestGuildActivityCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordGuildActivityListenerTest {

    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");
    private static final String GUILD_ID = "500000000000000001";
    private static final String CHANNEL_ID = "600000000000000001";
    private static final String MESSAGE_ID = "700000000000000001";
    private static final String USER_ID = "800000000000000001";
    private static final long GUILD_ID_LONG = 500000000000000001L;
    private static final long CHANNEL_ID_LONG = 600000000000000001L;
    private static final long USER_ID_LONG = 800000000000000001L;

    private final GuildActivityIngestionService ingestionService = mock(GuildActivityIngestionService.class);
    private final DiscordGuildActivityListener listener = new DiscordGuildActivityListener(
            ingestionService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void memberJoinMapsToNeutralCommandUsingJoinTimestampWhenAvailable() {
        GuildMemberJoinEvent event = mock(GuildMemberJoinEvent.class);
        Guild guild = guild();
        Member member = mock(Member.class);
        User user = user(false, false);
        OffsetDateTime joinedAt = OffsetDateTime.parse("2026-07-02T22:30:00Z");
        when(event.getGuild()).thenReturn(guild);
        when(event.getMember()).thenReturn(member);
        when(member.getUser()).thenReturn(user);
        when(member.hasTimeJoined()).thenReturn(true);
        when(member.getTimeJoined()).thenReturn(joinedAt);

        listener.onGuildMemberJoin(event);

        IngestGuildActivityCommand command = capturedCommand();
        assertThat(command.eventType()).isEqualTo(GuildActivityEventType.MEMBER_JOINED);
        assertThat(command.discordGuildId()).isEqualTo(GUILD_ID);
        assertThat(command.subjectDiscordId()).isEqualTo(USER_ID);
        assertThat(command.channelDiscordId()).isNull();
        assertThat(command.actorDiscordUserId()).isEqualTo(USER_ID);
        assertThat(command.actorBot()).isFalse();
        assertThat(command.occurredAt()).isEqualTo(joinedAt.toInstant());
        assertThat(command.sourceEventId())
                .isEqualTo("MEMBER_JOINED:" + GUILD_ID + ":" + USER_ID + ":" + joinedAt.toInstant());
    }

    @Test
    void memberLeaveUsesOneCapturedTimestampForOccurrenceAndBestEffortSourceId() {
        GuildMemberRemoveEvent event = mock(GuildMemberRemoveEvent.class);
        Guild guild = guild();
        User user = user(true, false);
        when(event.getGuild()).thenReturn(guild);
        when(event.getUser()).thenReturn(user);

        listener.onGuildMemberRemove(event);

        IngestGuildActivityCommand command = capturedCommand();
        assertThat(command.eventType()).isEqualTo(GuildActivityEventType.MEMBER_LEFT);
        assertThat(command.occurredAt()).isEqualTo(NOW);
        assertThat(command.actorBot()).isTrue();
        assertThat(command.sourceEventId())
                .isEqualTo("MEMBER_LEFT:" + GUILD_ID + ":" + USER_ID + ":" + NOW);
    }

    @Test
    void messageCreatedMapsIdsAndDoesNotReadMessageContent() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        Guild guild = guild();
        User user = user(false, false);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-02T23:59:00Z");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMessageId()).thenReturn(MESSAGE_ID);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID_LONG);
        when(event.getAuthor()).thenReturn(user);
        when(event.isWebhookMessage()).thenReturn(false);
        when(event.getMessage()).thenReturn(message);
        when(message.getTimeCreated()).thenReturn(createdAt);

        listener.onMessageReceived(event);

        IngestGuildActivityCommand command = capturedCommand();
        assertThat(command.eventType()).isEqualTo(GuildActivityEventType.MESSAGE_CREATED);
        assertThat(command.sourceEventId()).isEqualTo("MESSAGE_CREATED:" + GUILD_ID + ":" + MESSAGE_ID);
        assertThat(command.subjectDiscordId()).isEqualTo(MESSAGE_ID);
        assertThat(command.channelDiscordId()).isEqualTo(CHANNEL_ID);
        assertThat(command.actorDiscordUserId()).isEqualTo(USER_ID);
        assertThat(command.actorBot()).isFalse();
        assertThat(command.occurredAt()).isEqualTo(createdAt.toInstant());
        verify(message, never()).getContentRaw();
        verify(message, never()).getEmbeds();
        verify(message, never()).getAttachments();
    }

    @Test
    void directMessageIsIgnoredWithoutIngestion() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.isFromGuild()).thenReturn(false);

        listener.onMessageReceived(event);

        verify(ingestionService, never()).ingest(any());
    }

    @Test
    void webhookMessageCreatedIsRepresentedWithoutFabricatedActor() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        Guild guild = guild();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-02T23:59:00Z");
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMessageId()).thenReturn(MESSAGE_ID);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID_LONG);
        when(event.isWebhookMessage()).thenReturn(true);
        when(event.getMessage()).thenReturn(message);
        when(message.getTimeCreated()).thenReturn(createdAt);

        listener.onMessageReceived(event);

        IngestGuildActivityCommand command = capturedCommand();
        assertThat(command.actorDiscordUserId()).isNull();
        assertThat(command.actorBot()).isNull();
    }

    @Test
    void bulkDeleteCreatesOneDeletedEventPerMessageId() {
        MessageBulkDeleteEvent event = mock(MessageBulkDeleteEvent.class);
        GuildMessageChannelUnion channel = mock(GuildMessageChannelUnion.class);
        Guild guild = guild();
        when(event.getGuild()).thenReturn(guild);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID_LONG);
        when(event.getMessageIds()).thenReturn(List.of("700000000000000001", "700000000000000002"));

        listener.onMessageBulkDelete(event);

        ArgumentCaptor<IngestGuildActivityCommand> captor =
                ArgumentCaptor.forClass(IngestGuildActivityCommand.class);
        verify(ingestionService, org.mockito.Mockito.times(2)).ingest(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(IngestGuildActivityCommand::eventType)
                .containsExactly(GuildActivityEventType.MESSAGE_DELETED, GuildActivityEventType.MESSAGE_DELETED);
        assertThat(captor.getAllValues())
                .extracting(IngestGuildActivityCommand::subjectDiscordId)
                .containsExactly("700000000000000001", "700000000000000002");
        assertThat(captor.getAllValues())
                .extracting(IngestGuildActivityCommand::occurredAt)
                .containsExactly(NOW, NOW);
    }

    @Test
    void ingestionResultsAndFailuresDoNotEscapeJdaDispatch() {
        MessageReceivedEvent ignored = messageReceivedEvent();
        when(ingestionService.ingest(any())).thenReturn(GuildActivityIngestionResult.IGNORED_NOT_ONBOARDED);
        listener.onMessageReceived(ignored);

        MessageReceivedEvent failing = messageReceivedEvent();
        doThrow(new IllegalStateException("database down")).when(ingestionService).ingest(any());
        listener.onMessageReceived(failing);
    }

    private IngestGuildActivityCommand capturedCommand() {
        ArgumentCaptor<IngestGuildActivityCommand> captor =
                ArgumentCaptor.forClass(IngestGuildActivityCommand.class);
        verify(ingestionService).ingest(captor.capture());
        return captor.getValue();
    }

    private MessageReceivedEvent messageReceivedEvent() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        Guild guild = guild();
        User user = user(false, false);
        when(event.isFromGuild()).thenReturn(true);
        when(event.getGuild()).thenReturn(guild);
        when(event.getMessageId()).thenReturn(MESSAGE_ID);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getIdLong()).thenReturn(CHANNEL_ID_LONG);
        when(event.getAuthor()).thenReturn(user);
        when(event.isWebhookMessage()).thenReturn(false);
        when(event.getMessage()).thenReturn(message);
        when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-07-02T23:59:00Z"));
        return event;
    }

    private static Guild guild() {
        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(GUILD_ID_LONG);
        return guild;
    }

    private static User user(boolean bot, boolean system) {
        User user = mock(User.class);
        when(user.getIdLong()).thenReturn(USER_ID_LONG);
        when(user.isBot()).thenReturn(bot);
        when(user.isSystem()).thenReturn(system);
        return user;
    }
}
