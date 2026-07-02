package io.github.arturcarletto.guildos.discord;

import net.dv8tion.jda.api.JDA;

@FunctionalInterface
interface DiscordJdaFactory {

    JDA connect(String token);
}
