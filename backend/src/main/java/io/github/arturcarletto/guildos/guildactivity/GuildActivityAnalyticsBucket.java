package io.github.arturcarletto.guildos.guildactivity;

import java.time.Instant;

record GuildActivityAnalyticsBucket(
        Instant startedAt,
        long messagesCreated,
        long distinctMessagesEdited,
        long messagesDeleted,
        long humanMessages,
        long botMessages,
        long membersJoined,
        long membersLeft,
        long activeMembers,
        long activeChannels) {
}
