package io.github.arturcarletto.guildos.discord;

import java.time.Clock;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.discord.DiscordMemberMessageChannelResolver.DeliveryTarget;
import io.github.arturcarletto.guildos.discord.DiscordMemberMessageDeliveryMetrics.Outcome;
import io.github.arturcarletto.guildos.guildmembermessage.GuildMemberMessageService;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageDeliveryPlan;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;
import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageRenderContext;
import io.github.arturcarletto.guildos.guildmembermessage.RenderedMemberMessage;

/**
 * Delivers configured welcome and goodbye messages on real member lifecycle events. It reads the
 * detached, active configuration, resolves the channel and permissions, renders a platform-neutral
 * message and sends it asynchronously. No Discord request runs inside a database transaction, and a
 * delivery failure never crashes the Gateway listener or logs message content.
 */
final class DiscordMemberLifecycleListener extends ListenerAdapter {

    private static final Logger logger =
            LoggerFactory.getLogger(DiscordMemberLifecycleListener.class);

    private final GuildMemberMessageService service;
    private final DiscordMemberMessageEmbedFactory embedFactory;
    private final DiscordMemberMessageChannelResolver channelResolver;
    private final DiscordMemberMessageDeliveryMetrics metrics;
    private final Clock clock;

    DiscordMemberLifecycleListener(
            GuildMemberMessageService service,
            DiscordMemberMessageEmbedFactory embedFactory,
            DiscordMemberMessageChannelResolver channelResolver,
            DiscordMemberMessageDeliveryMetrics metrics,
            Clock clock) {
        this.service = service;
        this.embedFactory = embedFactory;
        this.channelResolver = channelResolver;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        deliver(
                guild,
                MemberMessageKind.WELCOME,
                member.getUser().isBot(),
                member.getUser().getId(),
                member.getEffectiveAvatarUrl(),
                () -> new MemberMessageRenderContext(
                        MarkdownSanitizer.escape(member.getEffectiveName()),
                        MarkdownSanitizer.escape(member.getUser().getName()),
                        MarkdownSanitizer.escape(guild.getName()),
                        guild.getMemberCount(),
                        member.getAsMention()));
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        deliver(
                guild,
                MemberMessageKind.GOODBYE,
                user.isBot(),
                null,
                user.getEffectiveAvatarUrl(),
                () -> new MemberMessageRenderContext(
                        MarkdownSanitizer.escape(user.getEffectiveName()),
                        MarkdownSanitizer.escape(user.getName()),
                        MarkdownSanitizer.escape(guild.getName()),
                        guild.getMemberCount(),
                        ""));
    }

    private void deliver(
            Guild guild,
            MemberMessageKind kind,
            boolean affectedIsBot,
            String mentionUserId,
            String avatarUrl,
            RenderContextSupplier contextSupplier) {
        String guildId = guild.getId();
        try {
            MemberMessageDeliveryPlan plan = service.resolveDelivery(guildId, kind);
            switch (plan.decision()) {
                case UNAVAILABLE, NOT_CONFIGURED -> metrics.record(kind, Outcome.NOT_CONFIGURED);
                case DISABLED -> metrics.record(kind, Outcome.DISABLED);
                case DELIVER -> {
                    if (affectedIsBot && !plan.appearance().includeBots()) {
                        metrics.record(kind, Outcome.BOT_IGNORED);
                        return;
                    }
                    send(guild, kind, plan, mentionUserId, avatarUrl, contextSupplier);
                }
                default -> { }
            }
        } catch (RuntimeException failure) {
            metrics.record(kind, Outcome.SEND_FAILED);
            logFailure(kind, guildId, null, "resolve", failure);
        }
    }

    private void send(
            Guild guild,
            MemberMessageKind kind,
            MemberMessageDeliveryPlan plan,
            String mentionUserId,
            String avatarUrl,
            RenderContextSupplier contextSupplier) {
        String guildId = guild.getId();
        DeliveryTarget target = channelResolver.resolveDelivery(guild, plan.channelId());
        switch (target.outcome()) {
            case CHANNEL_UNAVAILABLE -> {
                metrics.record(kind, Outcome.CHANNEL_UNAVAILABLE);
                return;
            }
            case PERMISSION_DENIED -> {
                metrics.record(kind, Outcome.PERMISSION_DENIED);
                return;
            }
            default -> { }
        }
        RenderedMemberMessage rendered = service.render(kind, plan.appearance(), contextSupplier.get());
        MessageCreateData message =
                embedFactory.publicMessage(rendered, avatarUrl, clock.instant(), mentionUserId);
        String channelId = target.channel().getId();
        try {
            target.channel().sendMessage(message).queue(
                    sent -> metrics.record(kind, Outcome.SENT),
                    failure -> {
                        metrics.record(kind, Outcome.SEND_FAILED);
                        logFailure(kind, guildId, channelId, "send", failure);
                    });
        } catch (RuntimeException failure) {
            metrics.record(kind, Outcome.SEND_FAILED);
            logFailure(kind, guildId, channelId, "send", failure);
        }
    }

    private void logFailure(
            MemberMessageKind kind, String guildId, String channelId, String operation, Throwable failure) {
        logger.warn(
                "Member message delivery failed: kind={}, guildId={}, channelId={}, operation={}, "
                        + "failureCategory={}",
                kind,
                guildId,
                channelId == null ? "none" : channelId,
                operation,
                failure.getClass().getSimpleName());
    }

    @FunctionalInterface
    private interface RenderContextSupplier {
        MemberMessageRenderContext get();
    }
}
