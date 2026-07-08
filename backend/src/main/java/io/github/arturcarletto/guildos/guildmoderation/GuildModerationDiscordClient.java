package io.github.arturcarletto.guildos.guildmoderation;

/**
 * Application-facing outbound port for Discord moderation actions.
 *
 * <p>Implementations must keep JDA and Discord response details inside the adapter boundary. They
 * should return only after Discord accepts the action or throw a controlled
 * {@link ModerationDiscordActionException}. No implementation should persist raw Discord payloads
 * or log moderation reasons.
 */
public interface GuildModerationDiscordClient {

    ModerationActionResult timeoutMember(TimeoutMemberCommand command);
}
