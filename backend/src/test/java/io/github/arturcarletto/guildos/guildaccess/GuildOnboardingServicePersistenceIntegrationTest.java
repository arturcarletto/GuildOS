package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({TestcontainersConfiguration.class, MutableClockTestConfiguration.class})
class GuildOnboardingServicePersistenceIntegrationTest {

    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(2, ChronoUnit.HOURS);
    private static final Instant INSTANT_C = INSTANT_A.plus(4, ChronoUnit.HOURS);
    private static final String TOKEN = "access-token";

    @Autowired
    private GuildOnboardingService service;

    @Autowired
    private OperatorGuildAccessRepository repository;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private MutableTestClock clock;

    @MockitoBean
    private DiscordGuildClient discordGuildClient;

    @BeforeEach
    void reset() {
        clock.setInstant(INSTANT_A);
        repository.deleteAll();
        repository.flush();
    }

    @Test
    void firstOnboardingCreatesAuthorization() {
        UUID operatorId = newOperator("1");
        String guildId = connectGuild("100000000000000001");
        stubOwnerGuild(guildId);

        OnboardingResult result = service.onboard(operatorId, guildId, TOKEN);

        assertThat(result.outcome()).isEqualTo(OnboardingOutcome.CREATED);
        assertThat(result.guild().role()).isEqualTo("OWNER");
        OperatorGuildAccess row = repository.findAll().get(0);
        assertThat(row.getOperatorId()).isEqualTo(operatorId);
        assertThat(row.getRegisteredGuildId()).isEqualTo(registeredGuildId(guildId));
        assertThat(row.getRole()).isEqualTo(GuildAccessRole.OWNER);
        assertThat(row.getGrantedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getRevokedAt()).isNull();
        assertThat(row.getVersion()).isZero();
    }

    @Test
    void repeatedSameRoleOnboardingIsUnchanged() {
        UUID operatorId = newOperator("2");
        String guildId = connectGuild("100000000000000002");
        stubOwnerGuild(guildId);

        service.onboard(operatorId, guildId, TOKEN);
        clock.setInstant(INSTANT_B);
        OnboardingResult second = service.onboard(operatorId, guildId, TOKEN);

        assertThat(second.outcome()).isEqualTo(OnboardingOutcome.UNCHANGED);
        assertThat(repository.count()).isEqualTo(1);
        OperatorGuildAccess row = repository.findAll().get(0);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getVersion()).isZero();
    }

    @Test
    void roleChangeUpdatesRoleAndTimestamp() {
        UUID operatorId = newOperator("3");
        String guildId = connectGuild("100000000000000003");
        stubAdminGuild(guildId);

        OnboardingResult first = service.onboard(operatorId, guildId, TOKEN);
        assertThat(first.outcome()).isEqualTo(OnboardingOutcome.CREATED);
        assertThat(first.guild().role()).isEqualTo("ADMIN");

        clock.setInstant(INSTANT_B);
        stubOwnerGuild(guildId);
        OnboardingResult second = service.onboard(operatorId, guildId, TOKEN);

        assertThat(second.outcome()).isEqualTo(OnboardingOutcome.ROLE_UPDATED);
        OperatorGuildAccess row = repository.findAll().get(0);
        assertThat(row.getRole()).isEqualTo(GuildAccessRole.OWNER);
        assertThat(row.getGrantedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_B);
        assertThat(row.getVersion()).isEqualTo(1);
    }

    @Test
    void revocationSoftPreservesRowAndReactivationReusesIt() {
        UUID operatorId = newOperator("4");
        String guildId = connectGuild("100000000000000004");
        stubOwnerGuild(guildId);

        service.onboard(operatorId, guildId, TOKEN);
        clock.setInstant(INSTANT_B);
        service.revoke(operatorId, guildId);

        OperatorGuildAccess revoked = repository.findAll().get(0);
        assertThat(revoked.getRevokedAt()).isEqualTo(INSTANT_B);
        assertThat(revoked.getUpdatedAt()).isEqualTo(INSTANT_B);
        assertThat(revoked.isActive()).isFalse();
        assertThat(service.listAuthorizedGuilds(operatorId)).isEmpty();

        clock.setInstant(INSTANT_C);
        OnboardingResult reactivated = service.onboard(operatorId, guildId, TOKEN);

        assertThat(reactivated.outcome()).isEqualTo(OnboardingOutcome.REACTIVATED);
        assertThat(repository.count()).isEqualTo(1);
        OperatorGuildAccess row = repository.findAll().get(0);
        assertThat(row.getRevokedAt()).isNull();
        assertThat(row.getGrantedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_C);
    }

    @Test
    void repeatedRevocationIsIdempotent() {
        UUID operatorId = newOperator("5");
        String guildId = connectGuild("100000000000000005");
        stubOwnerGuild(guildId);
        service.onboard(operatorId, guildId, TOKEN);

        clock.setInstant(INSTANT_B);
        service.revoke(operatorId, guildId);
        long versionAfterFirstRevoke = repository.findAll().get(0).getVersion();

        clock.setInstant(INSTANT_C);
        service.revoke(operatorId, guildId);

        OperatorGuildAccess row = repository.findAll().get(0);
        assertThat(row.getRevokedAt()).isEqualTo(INSTANT_B);
        assertThat(row.getVersion()).isEqualTo(versionAfterFirstRevoke);
    }

    @Test
    void revocationOfAnUnknownGuildIsANoOp() {
        UUID operatorId = newOperator("6");

        service.revoke(operatorId, "999999999999999999");

        assertThat(repository.count()).isZero();
    }

    @Test
    void authorizationIsIsolatedPerOperator() {
        UUID operatorOne = newOperator("7a");
        UUID operatorTwo = newOperator("7b");
        String guildId = connectGuild("100000000000000007");
        stubOwnerGuild(guildId);

        service.onboard(operatorOne, guildId, TOKEN);
        service.onboard(operatorTwo, guildId, TOKEN);

        assertThat(repository.count()).isEqualTo(2);
        assertThat(service.listAuthorizedGuilds(operatorOne)).hasSize(1);
        assertThat(service.listAuthorizedGuilds(operatorTwo)).hasSize(1);
        assertThat(repository.findActiveByOperatorId(operatorOne)).hasSize(1);
        assertThat(repository.findActiveByOperatorId(operatorTwo)).hasSize(1);
    }

    @Test
    void concurrentOnboardingDoesNotCreateDuplicateRelationships() throws Exception {
        UUID operatorId = newOperator("8");
        String guildId = connectGuild("100000000000000008");
        stubOwnerGuild(guildId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OnboardingOutcome> first = executor.submit(() -> onboardAfterSignal(operatorId, guildId, ready, start));
            Future<OnboardingOutcome> second = executor.submit(() -> onboardAfterSignal(operatorId, guildId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            OnboardingOutcome firstOutcome = first.get(10, TimeUnit.SECONDS);
            OnboardingOutcome secondOutcome = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(firstOutcome, secondOutcome))
                    .containsExactlyInAnyOrder(OnboardingOutcome.CREATED, OnboardingOutcome.UNCHANGED);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(repository.count()).isEqualTo(1);
    }

    private OnboardingOutcome onboardAfterSignal(
            UUID operatorId, String guildId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.onboard(operatorId, guildId, TOKEN).outcome();
    }

    private UUID newOperator(String suffix) {
        return operatorLoginService.login(
                new OperatorLoginCommand("op-" + suffix, "user-" + suffix, "User " + suffix, "avatar"))
                .operatorId();
    }

    private String connectGuild(String discordGuildId) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + discordGuildId));
        return discordGuildId;
    }

    private UUID registeredGuildId(String discordGuildId) {
        return guildDirectory.findByDiscordGuildId(discordGuildId).orElseThrow().registeredGuildId();
    }

    private void stubOwnerGuild(String discordGuildId) {
        when(discordGuildClient.fetchOperatorGuilds(anyString())).thenReturn(List.of(
                new OperatorDiscordGuild(discordGuildId, "Discord Guild", "icon", true, BigInteger.ZERO)));
    }

    private void stubAdminGuild(String discordGuildId) {
        when(discordGuildClient.fetchOperatorGuilds(anyString())).thenReturn(List.of(
                new OperatorDiscordGuild(discordGuildId, "Discord Guild", "icon", false, BigInteger.valueOf(8))));
    }
}
