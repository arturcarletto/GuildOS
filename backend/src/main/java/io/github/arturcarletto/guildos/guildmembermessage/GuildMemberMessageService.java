package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;

/** Platform-neutral welcome and goodbye configuration, preview, toggle and delivery use cases. */
@Service
public class GuildMemberMessageService {

    private final GuildDirectory guildDirectory;
    private final GuildOnboardingDirectory onboardingDirectory;
    private final GuildMemberMessageStore store;

    GuildMemberMessageService(
            GuildDirectory guildDirectory,
            GuildOnboardingDirectory onboardingDirectory,
            GuildMemberMessageStore store) {
        this.guildDirectory = guildDirectory;
        this.onboardingDirectory = onboardingDirectory;
        this.store = store;
    }

    public GuildMemberMessageView status(String discordGuildId, MemberMessageKind kind) {
        Access access = resolveAccess(discordGuildId, kind);
        if (access.result() != null) {
            return access.result();
        }
        return store.find(access.registeredGuildId(), kind)
                .map(stored -> GuildMemberMessageView.configured(access.guildName(), stored))
                .orElseGet(() -> GuildMemberMessageView.notConfigured(kind, access.guildName()));
    }

    public GuildMemberMessageView configure(
            String discordGuildId, MemberMessageKind kind, ConfigureMemberMessageCommand command) {
        Access access = resolveAccess(discordGuildId, kind);
        if (access.result() != null) {
            return access.result();
        }
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
            return GuildMemberMessageView.configured(access.guildName(), stored);
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildMemberMessageConflictException();
        }
    }

    public GuildMemberMessageView preview(
            String discordGuildId, MemberMessageKind kind, MemberMessageRenderContext context) {
        Access access = resolveAccess(discordGuildId, kind);
        if (access.result() != null) {
            return access.result();
        }
        return store.find(access.registeredGuildId(), kind)
                .map(stored -> GuildMemberMessageView.configured(
                        access.guildName(),
                        stored,
                        MemberMessageTemplateRenderer.renderMessage(kind, stored.appearance(), context)))
                .orElseGet(() -> GuildMemberMessageView.notConfigured(kind, access.guildName()));
    }

    public GuildMemberMessageView toggle(String discordGuildId, MemberMessageKind kind) {
        Access access = resolveAccess(discordGuildId, kind);
        if (access.result() != null) {
            return access.result();
        }
        Optional<StoredGuildMemberMessage> snapshot = store.find(access.registeredGuildId(), kind);
        if (snapshot.isEmpty()) {
            return GuildMemberMessageView.notConfigured(kind, access.guildName());
        }
        try {
            StoredGuildMemberMessage stored = store.toggleExisting(
                    access.registeredGuildId(), kind, snapshot.get().version());
            return GuildMemberMessageView.configured(access.guildName(), stored);
        } catch (OptimisticLockingFailureException exception) {
            throw new GuildMemberMessageConflictException();
        }
    }

    /**
     * Renders a delivery or preview message from a detached appearance and a platform-neutral
     * context. Pure formatting: it performs no access checks and touches no persistence.
     */
    public RenderedMemberMessage render(
            MemberMessageKind kind, MemberMessageAppearance appearance, MemberMessageRenderContext context) {
        return MemberMessageTemplateRenderer.renderMessage(kind, appearance, context);
    }

    /**
     * Resolves the active delivery configuration for a member lifecycle event. Returns detached,
     * immutable values so the Discord send can happen entirely outside a transaction.
     */
    public MemberMessageDeliveryPlan resolveDelivery(String discordGuildId, MemberMessageKind kind) {
        Access access = resolveAccess(discordGuildId, kind);
        if (access.result() != null) {
            return MemberMessageDeliveryPlan.unavailable(kind);
        }
        return store.find(access.registeredGuildId(), kind)
                .map(stored -> stored.enabled()
                        ? MemberMessageDeliveryPlan.deliver(stored)
                        : MemberMessageDeliveryPlan.disabled(kind))
                .orElseGet(() -> MemberMessageDeliveryPlan.notConfigured(kind));
    }

    private Access resolveAccess(String discordGuildId, MemberMessageKind kind) {
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(discordGuildId)
                .filter(RegisteredGuildView::connected)
                .orElse(null);
        if (guild == null) {
            return new Access(null, null, GuildMemberMessageView.unavailable(kind));
        }
        if (!onboardingDirectory.isOnboarded(discordGuildId)) {
            return new Access(null, null, GuildMemberMessageView.onboardingRequired(kind, guild.name()));
        }
        return new Access(guild.registeredGuildId(), guild.name(), null);
    }

    private record Access(UUID registeredGuildId, String guildName, GuildMemberMessageView result) {
    }
}
