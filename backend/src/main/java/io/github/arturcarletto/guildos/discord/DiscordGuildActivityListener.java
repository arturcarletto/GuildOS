package io.github.arturcarletto.guildos.discord;

import java.time.Clock;
import java.time.Instant;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.guildactivity.GuildActivityEventType;
import io.github.arturcarletto.guildos.guildactivity.GuildActivityIngestionService;
import io.github.arturcarletto.guildos.guildactivity.IngestGuildActivityCommand;

final class DiscordGuildActivityListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DiscordGuildActivityListener.class);

    private final GuildActivityIngestionService ingestionService;
    private final Clock clock;

    DiscordGuildActivityListener(GuildActivityIngestionService ingestionService, Clock clock) {
        this.ingestionService = ingestionService;
        this.clock = clock;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Instant capturedAt = clock.instant();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        User user = member.getUser();
        if (user.isSystem()) {
            return;
        }
        String guildId = snowflake(guild.getIdLong());
        String userId = snowflake(user.getIdLong());
        Instant occurredAt = member.hasTimeJoined() ? member.getTimeJoined().toInstant() : capturedAt;
        ingest(new IngestGuildActivityCommand(
                sourceId(GuildActivityEventType.MEMBER_JOINED, guildId, userId, occurredAt.toString()),
                GuildActivityEventType.MEMBER_JOINED,
                guildId,
                userId,
                null,
                userId,
                user.isBot(),
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION), null);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Instant occurredAt = clock.instant();
        Guild guild = event.getGuild();
        User user = event.getUser();
        if (user.isSystem()) {
            return;
        }
        String guildId = snowflake(guild.getIdLong());
        String userId = snowflake(user.getIdLong());
        ingest(new IngestGuildActivityCommand(
                sourceId(GuildActivityEventType.MEMBER_LEFT, guildId, userId, occurredAt.toString()),
                GuildActivityEventType.MEMBER_LEFT,
                guildId,
                userId,
                null,
                userId,
                user.isBot(),
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION), null);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        Actor actor = actor(event.getAuthor(), event.isWebhookMessage());
        String guildId = snowflake(event.getGuild().getIdLong());
        String channelId = snowflake(event.getChannel().getIdLong());
        ingest(new IngestGuildActivityCommand(
                sourceId(GuildActivityEventType.MESSAGE_CREATED, guildId, event.getMessageId()),
                GuildActivityEventType.MESSAGE_CREATED,
                guildId,
                event.getMessageId(),
                channelId,
                actor.userId(),
                actor.bot(),
                event.getMessage().getTimeCreated().toInstant(),
                IngestGuildActivityCommand.SCHEMA_VERSION), event);
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        Instant occurredAt = clock.instant();
        String guildId = snowflake(event.getGuild().getIdLong());
        String channelId = snowflake(event.getChannel().getIdLong());
        ingest(new IngestGuildActivityCommand(
                sourceId(GuildActivityEventType.MESSAGE_EDITED, guildId, event.getMessageId()),
                GuildActivityEventType.MESSAGE_EDITED,
                guildId,
                event.getMessageId(),
                channelId,
                null,
                null,
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION), event);
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        Instant occurredAt = clock.instant();
        String guildId = snowflake(event.getGuild().getIdLong());
        String channelId = snowflake(event.getChannel().getIdLong());
        ingest(new IngestGuildActivityCommand(
                sourceId(GuildActivityEventType.MESSAGE_DELETED, guildId, event.getMessageId()),
                GuildActivityEventType.MESSAGE_DELETED,
                guildId,
                event.getMessageId(),
                channelId,
                null,
                null,
                occurredAt,
                IngestGuildActivityCommand.SCHEMA_VERSION), event);
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        Instant occurredAt = clock.instant();
        String guildId = snowflake(event.getGuild().getIdLong());
        String channelId = snowflake(event.getChannel().getIdLong());
        for (String messageId : event.getMessageIds()) {
            ingest(new IngestGuildActivityCommand(
                    sourceId(GuildActivityEventType.MESSAGE_DELETED, guildId, messageId),
                    GuildActivityEventType.MESSAGE_DELETED,
                    guildId,
                    messageId,
                    channelId,
                    null,
                    null,
                    occurredAt,
                    IngestGuildActivityCommand.SCHEMA_VERSION), guildId, channelId, messageId);
        }
    }

    private void ingest(IngestGuildActivityCommand command, GenericMessageEvent event) {
        ingest(command, command.discordGuildId(), command.channelDiscordId(), event == null ? null : event.getMessageId());
    }

    private void ingest(IngestGuildActivityCommand command, String guildId, String channelId, String messageId) {
        try {
            ingestionService.ingest(command);
        } catch (RuntimeException failure) {
            logger.warn(
                    "Discord activity ingestion failed: eventType={}, guildId={}, channelId={}, "
                            + "messageId={}, failureCategory={}",
                    command.eventType(),
                    guildId,
                    channelId == null ? "none" : channelId,
                    messageId == null ? "none" : messageId,
                    failureCategory(failure));
        }
    }

    private static Actor actor(User user, boolean webhookMessage) {
        if (webhookMessage || user == null || user.isSystem()) {
            return new Actor(null, null);
        }
        return new Actor(snowflake(user.getIdLong()), user.isBot());
    }

    private static String sourceId(
            GuildActivityEventType eventType, String guildId, String subjectId, String extra) {
        return "%s:%s:%s:%s".formatted(eventType.name(), guildId, subjectId, extra);
    }

    private static String sourceId(GuildActivityEventType eventType, String guildId, String subjectId) {
        return "%s:%s:%s".formatted(eventType.name(), guildId, subjectId);
    }

    private static String failureCategory(Throwable failure) {
        String category = failure.getClass().getSimpleName();
        return category == null || category.isBlank() ? "UnknownFailure" : category;
    }

    private static String snowflake(long value) {
        return Long.toUnsignedString(value);
    }

    private record Actor(String userId, Boolean bot) {
    }
}
