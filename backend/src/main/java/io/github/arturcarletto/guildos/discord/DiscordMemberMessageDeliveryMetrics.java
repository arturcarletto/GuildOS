package io.github.arturcarletto.guildos.discord;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import io.github.arturcarletto.guildos.guildmembermessage.MemberMessageKind;

/**
 * Bounded Micrometer counters for member-message delivery. Tags are limited to the message kind and
 * a small closed set of outcomes; guild, channel and user identifiers are never used as tags.
 */
final class DiscordMemberMessageDeliveryMetrics {

    static final String METRIC = "guildos.discord.member_message.delivery";

    enum Outcome {
        SENT("sent"),
        DISABLED("disabled"),
        NOT_CONFIGURED("not_configured"),
        BOT_IGNORED("bot_ignored"),
        CHANNEL_UNAVAILABLE("channel_unavailable"),
        PERMISSION_DENIED("permission_denied"),
        SEND_FAILED("send_failed");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private final MeterRegistry registry;

    DiscordMemberMessageDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void record(MemberMessageKind kind, Outcome outcome) {
        Counter.builder(METRIC)
                .tag("kind", kind.name().toLowerCase(Locale.ROOT))
                .tag("outcome", outcome.tag())
                .register(registry)
                .increment();
    }
}
