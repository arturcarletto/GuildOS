package io.github.arturcarletto.guildos.guildsettings;

/** Safe, read-only settings projection for other backend capabilities. */
public record GuildSettingsView(String timezone, String locale, long version) {
}
