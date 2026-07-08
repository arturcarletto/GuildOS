package io.github.arturcarletto.guildos.discord;

import java.util.Objects;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.arturcarletto.guildos.guildmoderation.GuildModerationDiscordClient;
import io.github.arturcarletto.guildos.guildmoderation.ModerationActionResult;
import io.github.arturcarletto.guildos.guildmoderation.ModerationDiscordActionException;
import io.github.arturcarletto.guildos.guildmoderation.ModerationFailureCategory;
import io.github.arturcarletto.guildos.guildmoderation.TimeoutMemberCommand;

final class JdaGuildModerationDiscordClient implements GuildModerationDiscordClient {

    private static final Logger logger = LoggerFactory.getLogger(JdaGuildModerationDiscordClient.class);

    private final DiscordGateway gateway;

    JdaGuildModerationDiscordClient(DiscordGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    @Override
    public ModerationActionResult timeoutMember(TimeoutMemberCommand command) {
        try {
            JDA jda = gateway.jda()
                    .orElseThrow(() -> failure(command, ModerationFailureCategory.DISCORD_UNAVAILABLE));
            Guild guild = jda.getGuildById(command.discordGuildId());
            if (guild == null) {
                throw failure(command, ModerationFailureCategory.GUILD_UNAVAILABLE);
            }
            if (!guild.getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                throw failure(command, ModerationFailureCategory.BOT_PERMISSION_MISSING);
            }

            Member target = guild.retrieveMemberById(command.targetUserId()).complete();
            AuditableRestAction<Void> action = target.timeoutFor(command.duration());
            command.reason().ifPresent(action::reason);
            action.complete();
            return ModerationActionResult.success();
        } catch (ModerationDiscordActionException exception) {
            throw exception;
        } catch (InsufficientPermissionException exception) {
            throw failure(command, ModerationFailureCategory.BOT_PERMISSION_MISSING, exception);
        } catch (HierarchyException | IllegalArgumentException exception) {
            throw failure(command, ModerationFailureCategory.DISCORD_REJECTED, exception);
        } catch (ErrorResponseException exception) {
            throw failure(command, categoryFor(exception), exception);
        } catch (RuntimeException exception) {
            throw failure(command, ModerationFailureCategory.DISCORD_UNAVAILABLE, exception);
        }
    }

    private static ModerationFailureCategory categoryFor(ErrorResponseException exception) {
        ErrorResponse response = exception.getErrorResponse();
        if (response == ErrorResponse.UNKNOWN_GUILD) {
            return ModerationFailureCategory.GUILD_UNAVAILABLE;
        }
        if (response == ErrorResponse.UNKNOWN_MEMBER || response == ErrorResponse.UNKNOWN_USER) {
            return ModerationFailureCategory.TARGET_NOT_FOUND;
        }
        if (response == ErrorResponse.MISSING_PERMISSIONS || response == ErrorResponse.MISSING_ACCESS) {
            return ModerationFailureCategory.BOT_PERMISSION_MISSING;
        }
        if (exception.isServerError()) {
            return ModerationFailureCategory.DISCORD_UNAVAILABLE;
        }
        return ModerationFailureCategory.DISCORD_REJECTED;
    }

    private static ModerationDiscordActionException failure(
            TimeoutMemberCommand command,
            ModerationFailureCategory category) {
        logFailure(command, category, null);
        return new ModerationDiscordActionException(category);
    }

    private static ModerationDiscordActionException failure(
            TimeoutMemberCommand command,
            ModerationFailureCategory category,
            Throwable failure) {
        logFailure(command, category, failure);
        return new ModerationDiscordActionException(category);
    }

    private static void logFailure(
            TimeoutMemberCommand command,
            ModerationFailureCategory category,
            Throwable failure) {
        logger.warn(
                "Discord moderation action failed: actionType=MEMBER_TIMEOUT, guildId={}, targetUserId={}, "
                        + "failureCategory={}, failureClass={}",
                command.discordGuildId(),
                command.targetUserId(),
                category,
                failure == null ? "none" : failure.getClass().getSimpleName());
    }
}
