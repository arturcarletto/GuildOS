package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * The outcome of a delivery lookup for a member lifecycle event. When {@link Decision#DELIVER}, it
 * carries the detached, immutable channel and appearance the Discord adapter must render and send
 * outside of any database transaction. It never carries the internal version.
 */
public record MemberMessageDeliveryPlan(
        Decision decision,
        MemberMessageKind kind,
        String channelId,
        MemberMessageAppearance appearance) {

    public enum Decision {
        UNAVAILABLE,
        NOT_CONFIGURED,
        DISABLED,
        DELIVER
    }

    static MemberMessageDeliveryPlan unavailable(MemberMessageKind kind) {
        return new MemberMessageDeliveryPlan(Decision.UNAVAILABLE, kind, null, null);
    }

    static MemberMessageDeliveryPlan notConfigured(MemberMessageKind kind) {
        return new MemberMessageDeliveryPlan(Decision.NOT_CONFIGURED, kind, null, null);
    }

    static MemberMessageDeliveryPlan disabled(MemberMessageKind kind) {
        return new MemberMessageDeliveryPlan(Decision.DISABLED, kind, null, null);
    }

    static MemberMessageDeliveryPlan deliver(StoredGuildMemberMessage stored) {
        return new MemberMessageDeliveryPlan(
                Decision.DELIVER, stored.kind(), stored.channelId(), stored.appearance());
    }

    public boolean shouldDeliver() {
        return decision == Decision.DELIVER;
    }
}
