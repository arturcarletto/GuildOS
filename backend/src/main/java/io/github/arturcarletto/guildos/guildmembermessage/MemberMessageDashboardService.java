package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guildaudit.GuildAuditEventType;
import io.github.arturcarletto.guildos.guildaudit.GuildAuditRecorder;
import io.github.arturcarletto.guildos.guildaccess.AuthorizedGuildAccess;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessAuthorizer;

/**
 * Authorized dashboard use cases for welcome/goodbye automation.
 *
 * <p>Authorization uses the operator-to-guild boundary ({@link GuildAccessAuthorizer}) — the same
 * boundary the settings dashboard uses — so an operator can only manage automation for guilds where
 * they hold an active {@code OWNER}/{@code ADMIN} authorization. All domain validation, persistence,
 * and rendering are reused from the existing member-message components ({@link GuildMemberMessageStore},
 * {@link MemberMessageAppearanceFactory}, {@link MemberMessageTemplateRenderer}), so the dashboard and
 * the {@code /welcome}/{@code /goodbye} slash commands share one set of rules.
 *
 * <p>Mutations run in a single transaction that first takes the authorization row lock via
 * {@code findActiveForUpdate}, so a concurrent access revocation cannot slip a write through, and no
 * Discord call happens inside the transaction.
 */
@Service
class MemberMessageDashboardService {

    private static final Pattern CHANNEL_SNOWFLAKE = Pattern.compile("^[0-9]{1,20}$");

    private final GuildAccessAuthorizer authorizer;
    private final GuildMemberMessageStore store;
    private final GuildAuditRecorder auditRecorder;

    MemberMessageDashboardService(
            GuildAccessAuthorizer authorizer,
            GuildMemberMessageStore store,
            GuildAuditRecorder auditRecorder) {
        this.authorizer = authorizer;
        this.store = store;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    MemberMessageConfigResponse get(UUID operatorId, String discordGuildId, MemberMessageKind kind) {
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(MemberMessageAccessNotFoundException::new);
        return store.find(access.registeredGuildId(), kind)
                .map(MemberMessageConfigResponse::configured)
                .orElseGet(() -> MemberMessageConfigResponse.notConfigured(kind));
    }

    @Transactional
    MemberMessageConfigResponse configure(
            UUID operatorId,
            String discordGuildId,
            MemberMessageKind kind,
            ConfigureMemberMessageCommand command) {
        AuthorizedGuildAccess access = authorizer.findActiveForUpdate(operatorId, discordGuildId)
                .orElseThrow(MemberMessageAccessNotFoundException::new);
        requireChannelSnowflake(command.channelId());

        Optional<StoredGuildMemberMessage> snapshot = store.find(access.registeredGuildId(), kind);
        try {
            StoredGuildMemberMessage stored = snapshot
                    .map(current -> store.configureExisting(
                            access.registeredGuildId(),
                            kind,
                            command.channelId(),
                            MemberMessageAppearanceFactory.forUpdate(kind, command, current.appearance()),
                            current.version()))
                    .orElseGet(() -> store.createIfAbsent(
                            access.registeredGuildId(),
                            kind,
                            command.channelId(),
                            MemberMessageAppearanceFactory.forCreate(kind, command)));
            auditRecorder.recordOperatorEvent(
                    access.registeredGuildId(),
                    operatorId,
                    configuredAuditEventType(kind));
            return MemberMessageConfigResponse.configured(stored);
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildMemberMessageConflictException();
        }
    }

    @Transactional
    MemberMessageConfigResponse toggle(UUID operatorId, String discordGuildId, MemberMessageKind kind) {
        AuthorizedGuildAccess access = authorizer.findActiveForUpdate(operatorId, discordGuildId)
                .orElseThrow(MemberMessageAccessNotFoundException::new);
        StoredGuildMemberMessage snapshot = store.find(access.registeredGuildId(), kind)
                .orElseThrow(MemberMessageNotConfiguredException::new);
        try {
            StoredGuildMemberMessage stored =
                    store.toggleExisting(access.registeredGuildId(), kind, snapshot.version());
            auditRecorder.recordOperatorEvent(
                    access.registeredGuildId(),
                    operatorId,
                    toggledAuditEventType(kind));
            return MemberMessageConfigResponse.configured(stored);
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildMemberMessageConflictException();
        }
    }

    /**
     * Validates the supplied draft and renders it against deterministic sample values. It never
     * persists anything and never contacts Discord.
     */
    @Transactional(readOnly = true)
    MemberMessagePreviewResponse preview(
            UUID operatorId,
            String discordGuildId,
            MemberMessageKind kind,
            ConfigureMemberMessageCommand command) {
        AuthorizedGuildAccess access = authorizer.findActive(operatorId, discordGuildId)
                .orElseThrow(MemberMessageAccessNotFoundException::new);
        requireChannelSnowflake(command.channelId());
        MemberMessageAppearance appearance = MemberMessageAppearanceFactory.forCreate(kind, command);
        RenderedMemberMessage rendered = MemberMessageTemplateRenderer.renderMessage(
                kind, appearance, MemberMessageSamples.context(kind, access.name()));
        return MemberMessagePreviewResponse.from(kind, rendered);
    }

    private static void requireChannelSnowflake(String channelId) {
        if (channelId == null || !CHANNEL_SNOWFLAKE.matcher(channelId).matches()) {
            throw new InvalidMemberMessageConfigurationException(
                    "The channel id must be a Discord channel id (a numeric snowflake)");
        }
    }

    private static GuildAuditEventType configuredAuditEventType(MemberMessageKind kind) {
        return switch (kind) {
            case WELCOME -> GuildAuditEventType.WELCOME_CONFIGURED;
            case GOODBYE -> GuildAuditEventType.GOODBYE_CONFIGURED;
        };
    }

    private static GuildAuditEventType toggledAuditEventType(MemberMessageKind kind) {
        return switch (kind) {
            case WELCOME -> GuildAuditEventType.WELCOME_TOGGLED;
            case GOODBYE -> GuildAuditEventType.GOODBYE_TOGGLED;
        };
    }
}
