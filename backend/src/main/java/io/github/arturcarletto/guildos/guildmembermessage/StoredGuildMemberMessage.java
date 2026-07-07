package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * A detached, immutable snapshot of a persisted member-message configuration. It carries the
 * internal optimistic-locking {@code version} for concurrency control only; the version is never
 * surfaced to Discord users.
 */
record StoredGuildMemberMessage(
        MemberMessageKind kind,
        boolean enabled,
        String channelId,
        MemberMessageAppearance appearance,
        long version) {
}
