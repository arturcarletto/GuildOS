package io.github.arturcarletto.guildos.guildaccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;

/**
 * Application service coordinating guild onboarding and operator-to-guild authorization.
 *
 * <p>Orchestration runs with no transaction open: the outbound Discord call and eligibility and
 * bot-presence checks happen first, and only the persistence step enters a short transaction via
 * {@link OperatorGuildAccessStore}. Eligibility is always verified server-side from the operator's
 * current Discord guild list; the OAuth access token is only ever a transient parameter forwarded
 * to the Discord client, never stored or exposed.
 */
@Service
public class GuildOnboardingService {

    private final OperatorGuildAccessRepository repository;
    private final GuildDirectory guildDirectory;
    private final DiscordGuildClient discordGuildClient;
    private final OperatorGuildAccessStore accessStore;

    GuildOnboardingService(
            OperatorGuildAccessRepository repository,
            GuildDirectory guildDirectory,
            DiscordGuildClient discordGuildClient,
            OperatorGuildAccessStore accessStore) {
        this.repository = repository;
        this.guildDirectory = guildDirectory;
        this.discordGuildClient = discordGuildClient;
        this.accessStore = accessStore;
    }

    public List<EligibleGuildResponse> listEligibleGuilds(UUID operatorId, String accessToken) {
        List<OperatorDiscordGuild> eligible = discordGuildClient.fetchOperatorGuilds(accessToken).stream()
                .filter(DiscordGuildEligibility::isEligible)
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }

        Map<String, RegisteredGuildView> connectedByDiscordId = guildDirectory
                .findAllByDiscordGuildIds(eligible.stream().map(OperatorDiscordGuild::discordGuildId).toList())
                .stream()
                .filter(RegisteredGuildView::connected)
                .collect(Collectors.toMap(RegisteredGuildView::discordGuildId, Function.identity()));
        if (connectedByDiscordId.isEmpty()) {
            return List.of();
        }

        Set<UUID> registeredGuildIds = connectedByDiscordId.values().stream()
                .map(RegisteredGuildView::registeredGuildId)
                .collect(Collectors.toSet());
        Map<UUID, OperatorGuildAccess> accessByRegisteredId = repository
                .findByOperatorIdAndRegisteredGuildIdIn(operatorId, registeredGuildIds).stream()
                .collect(Collectors.toMap(OperatorGuildAccess::getRegisteredGuildId, Function.identity()));

        List<EligibleGuildResponse> responses = new ArrayList<>();
        for (OperatorDiscordGuild guild : eligible) {
            RegisteredGuildView view = connectedByDiscordId.get(guild.discordGuildId());
            if (view == null) {
                continue;
            }
            OnboardingStatus status = statusOf(accessByRegisteredId.get(view.registeredGuildId()));
            responses.add(new EligibleGuildResponse(
                    guild.discordGuildId(),
                    guild.name(),
                    guild.iconHash(),
                    DiscordGuildEligibility.roleOf(guild).name(),
                    status.name()));
        }
        return responses;
    }

    public OnboardingResult onboard(UUID operatorId, String discordGuildId, String accessToken) {
        OperatorDiscordGuild target = discordGuildClient.fetchOperatorGuilds(accessToken).stream()
                .filter(guild -> guild.discordGuildId().equals(discordGuildId))
                .findFirst()
                .orElseThrow(() -> new OperatorGuildEligibilityException(
                        "Operator is not eligible to onboard the requested guild"));
        if (!DiscordGuildEligibility.isEligible(target)) {
            throw new OperatorGuildEligibilityException(
                    "Operator is not eligible to onboard the requested guild");
        }

        RegisteredGuildView view = guildDirectory.findByDiscordGuildId(discordGuildId)
                .filter(RegisteredGuildView::connected)
                .orElseThrow(() -> new GuildNotOnboardableException(
                        "Guild is unknown to Guild OS or the bot is not connected"));

        GuildAccessRole role = DiscordGuildEligibility.roleOf(target);
        OnboardingOutcome outcome = accessStore.onboard(operatorId, view.registeredGuildId(), role);

        AuthorizedGuildResponse guild = new AuthorizedGuildResponse(
                view.discordGuildId(), view.name(), role.name());
        return new OnboardingResult(outcome, guild);
    }

    @Transactional(readOnly = true)
    public List<AuthorizedGuildResponse> listAuthorizedGuilds(UUID operatorId) {
        List<OperatorGuildAccess> active = repository.findActiveByOperatorId(operatorId);
        if (active.isEmpty()) {
            return List.of();
        }
        Map<UUID, RegisteredGuildView> viewsByRegisteredId = guildDirectory
                .findAllByRegisteredGuildIds(active.stream()
                        .map(OperatorGuildAccess::getRegisteredGuildId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(RegisteredGuildView::registeredGuildId, Function.identity()));

        List<AuthorizedGuildResponse> responses = new ArrayList<>();
        for (OperatorGuildAccess access : active) {
            RegisteredGuildView view = viewsByRegisteredId.get(access.getRegisteredGuildId());
            if (view == null) {
                continue;
            }
            responses.add(new AuthorizedGuildResponse(
                    view.discordGuildId(), view.name(), access.getRole().name()));
        }
        return responses;
    }

    public void revoke(UUID operatorId, String discordGuildId) {
        Optional<RegisteredGuildView> view = guildDirectory.findByDiscordGuildId(discordGuildId);
        if (view.isEmpty()) {
            return;
        }
        accessStore.revoke(operatorId, view.get().registeredGuildId());
    }

    private static OnboardingStatus statusOf(OperatorGuildAccess access) {
        if (access == null) {
            return OnboardingStatus.AVAILABLE;
        }
        return access.isActive() ? OnboardingStatus.ONBOARDED : OnboardingStatus.REVOKED;
    }
}
