package io.github.arturcarletto.guildos.guildwelcome;

record StoredGuildWelcome(
        boolean enabled,
        String channelId,
        String messageTemplate,
        long version) {
}
