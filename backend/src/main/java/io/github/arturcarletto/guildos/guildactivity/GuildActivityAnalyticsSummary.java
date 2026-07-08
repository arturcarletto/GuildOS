package io.github.arturcarletto.guildos.guildactivity;

record GuildActivityAnalyticsSummary(
        long messagesCreated,
        long distinctMessagesEdited,
        long messagesDeleted,
        long humanMessages,
        long botMessages,
        long membersJoined,
        long membersLeft,
        long peakHourlyActiveMembers,
        long peakHourlyActiveChannels) {

    static GuildActivityAnalyticsSummary from(Iterable<GuildActivityAnalyticsBucket> buckets) {
        long messagesCreated = 0;
        long distinctMessagesEdited = 0;
        long messagesDeleted = 0;
        long humanMessages = 0;
        long botMessages = 0;
        long membersJoined = 0;
        long membersLeft = 0;
        long peakHourlyActiveMembers = 0;
        long peakHourlyActiveChannels = 0;

        for (GuildActivityAnalyticsBucket bucket : buckets) {
            messagesCreated += bucket.messagesCreated();
            distinctMessagesEdited += bucket.distinctMessagesEdited();
            messagesDeleted += bucket.messagesDeleted();
            humanMessages += bucket.humanMessages();
            botMessages += bucket.botMessages();
            membersJoined += bucket.membersJoined();
            membersLeft += bucket.membersLeft();
            peakHourlyActiveMembers = Math.max(peakHourlyActiveMembers, bucket.activeMembers());
            peakHourlyActiveChannels = Math.max(peakHourlyActiveChannels, bucket.activeChannels());
        }

        return new GuildActivityAnalyticsSummary(
                messagesCreated,
                distinctMessagesEdited,
                messagesDeleted,
                humanMessages,
                botMessages,
                membersJoined,
                membersLeft,
                peakHourlyActiveMembers,
                peakHourlyActiveChannels);
    }
}
