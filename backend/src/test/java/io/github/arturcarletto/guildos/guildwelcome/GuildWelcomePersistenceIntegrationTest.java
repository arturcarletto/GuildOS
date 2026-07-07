package io.github.arturcarletto.guildos.guildwelcome;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static io.github.arturcarletto.guildos.MutableClockTestConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class,
        GuildWelcomePersistenceIntegrationTest.ConcurrencyConfiguration.class
})
class GuildWelcomePersistenceIntegrationTest {

    private static final String GUILD_ID = "810000000000000001";

    @Autowired
    private GuildWelcomeService service;

    @Autowired
    private GuildWelcomeStore store;

    @Autowired
    private GuildWelcomeConfigurationRepository repository;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConcurrencyFixture concurrencyFixture;

    @BeforeEach
    void clearWelcomeData() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_welcome_configurations");
        accessFixture.clear();
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void flywayCreatesValidatedTableForeignKeyAndUniqueGuildConstraint() {
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'guild_os'
                          AND table_name = 'guild_welcome_configurations'
                        """,
                        Integer.class))
                .isEqualTo(1);

        assertThatThrownBy(() -> insertRaw(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        RegisteredGuildView guild = onboard(GUILD_ID, "constraints");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        assertThatThrownBy(() -> insertRaw(UUID.randomUUID(), guild.registeredGuildId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusAndPreviewAreReadOnlyAndMissingDisableCreatesNothing() {
        onboard(GUILD_ID, "reads");

        assertThat(service.status(GUILD_ID).state()).isEqualTo(GuildWelcomeState.NOT_CONFIGURED);
        assertThat(service.preview(
                        GUILD_ID,
                        new WelcomePreviewContext("Artur", "Heaven", 42))
                .state()).isEqualTo(GuildWelcomeState.NOT_CONFIGURED);
        assertThat(service.disable(GUILD_ID).state()).isEqualTo(GuildWelcomeState.NOT_CONFIGURED);

        assertThat(repository.count()).isZero();
    }

    @Test
    void configureNoOpUpdateDisableAndReenablePreserveRequiredPersistenceSemantics() {
        RegisteredGuildView guild = onboard(GUILD_ID, "lifecycle");

        GuildWelcomeView created = service.configure(
                GUILD_ID, "820000000000000001", " \r\nWelcome {member}!\r\n ");
        GuildWelcomeConfiguration initial = find(guild);
        UUID internalId = initial.getId();
        assertThat(created.enabled()).isTrue();
        assertThat(created.messageTemplate()).isEqualTo("Welcome {member}!");
        assertThat(initial.getCreatedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(initial.getUpdatedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(initial.getVersion()).isZero();

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(1)));
        GuildWelcomeView unchanged = service.configure(
                GUILD_ID, "820000000000000001", "Welcome {member}!");
        GuildWelcomeConfiguration afterNoOp = find(guild);
        assertThat(unchanged.version()).isZero();
        assertThat(afterNoOp.getUpdatedAt()).isEqualTo(INITIAL_INSTANT);

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(2)));
        GuildWelcomeView updated = service.configure(
                GUILD_ID, "820000000000000002", "Welcome to {server}!");
        GuildWelcomeConfiguration afterUpdate = find(guild);
        assertThat(updated.version()).isEqualTo(1);
        assertThat(afterUpdate.getId()).isEqualTo(internalId);
        assertThat(afterUpdate.getCreatedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(afterUpdate.getUpdatedAt()).isEqualTo(INITIAL_INSTANT.plus(Duration.ofMinutes(2)));

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(3)));
        GuildWelcomeView disabled = service.disable(GUILD_ID);
        GuildWelcomeConfiguration afterDisable = find(guild);
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.version()).isEqualTo(2);
        assertThat(afterDisable.getChannelId()).isEqualTo("820000000000000002");
        assertThat(afterDisable.getMessageTemplate()).isEqualTo("Welcome to {server}!");

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(4)));
        GuildWelcomeView disabledAgain = service.disable(GUILD_ID);
        GuildWelcomeConfiguration afterDisableNoOp = find(guild);
        assertThat(disabledAgain.version()).isEqualTo(2);
        assertThat(afterDisableNoOp.getUpdatedAt())
                .isEqualTo(INITIAL_INSTANT.plus(Duration.ofMinutes(3)));

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(5)));
        GuildWelcomeView reenabled = service.configure(
                GUILD_ID, "820000000000000002", "Welcome to {server}!");
        GuildWelcomeConfiguration afterReenable = find(guild);
        assertThat(reenabled.enabled()).isTrue();
        assertThat(reenabled.version()).isEqualTo(3);
        assertThat(afterReenable.getId()).isEqualTo(internalId);
        assertThat(afterReenable.getCreatedAt()).isEqualTo(INITIAL_INSTANT);
    }

    @Test
    void revokedOnlyOnboardingAndDisconnectedGuildCannotCreateConfiguration() {
        RegisteredGuildView revoked = onboard(GUILD_ID, "revoked");
        UUID operatorId = operator("revoked-second");
        accessFixture.authorizeOwner(operatorId, revoked.registeredGuildId());
        accessFixture.revoke(operatorId, revoked.registeredGuildId());
        // Revoke the authorization created by onboard as well.
        UUID firstOperator = operatorIdFor("revoked");
        accessFixture.revoke(firstOperator, revoked.registeredGuildId());

        assertThat(service.configure(GUILD_ID, "820000000000000001", "Welcome").state())
                .isEqualTo(GuildWelcomeState.ONBOARDING_REQUIRED);
        assertThat(repository.count()).isZero();

        RegisteredGuildView disconnected = onboard("810000000000000002", "disconnected");
        guildConnectionService.disconnect(new DisconnectGuildCommand(disconnected.discordGuildId()));
        assertThat(service.configure(
                        disconnected.discordGuildId(),
                        "820000000000000001",
                        "Welcome")
                .state()).isEqualTo(GuildWelcomeState.UNAVAILABLE);
        assertThat(repository.count()).isZero();
    }

    @Test
    void concurrentInitialConfigureProducesExactlyOneSuccessAndOneControlledConflict()
            throws Exception {
        RegisteredGuildView guild = onboard(GUILD_ID, "concurrent-initial");
        CountDownLatch bothSnapshotted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() ->
                    concurrencyFixture.createAfterSharedAbsentSnapshot(
                            guild.registeredGuildId(),
                            "820000000000000001",
                            "Welcome one",
                            bothSnapshotted));
            Future<String> second = executor.submit(() ->
                    concurrencyFixture.createAfterSharedAbsentSnapshot(
                            guild.registeredGuildId(),
                            "820000000000000002",
                            "Welcome two",
                            bothSnapshotted));

            String firstOutcome = first.get(10, TimeUnit.SECONDS);
            String secondOutcome = second.get(10, TimeUnit.SECONDS);

            // Exactly one success, exactly one controlled conflict — never two silent successes.
            assertThat(List.of(firstOutcome, secondOutcome))
                    .containsExactlyInAnyOrder("success", "conflict");
            assertThat(repository.count()).isEqualTo(1);

            GuildWelcomeConfiguration stored = find(guild);
            if (firstOutcome.equals("success")) {
                assertThat(stored.getChannelId()).isEqualTo("820000000000000001");
                assertThat(stored.getMessageTemplate()).isEqualTo("Welcome one");
            } else {
                assertThat(stored.getChannelId()).isEqualTo("820000000000000002");
                assertThat(stored.getMessageTemplate()).isEqualTo("Welcome two");
            }
        } finally {
            bothSnapshotted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void createIfAbsentOnAnAlreadyPresentRowIsAControlledConflict() {
        RegisteredGuildView guild = onboard(GUILD_ID, "create-race");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");

        assertThatThrownBy(() -> store.createIfAbsent(
                        guild.registeredGuildId(), "820000000000000002", "Other"))
                .isInstanceOf(GuildWelcomeConflictException.class);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(find(guild).getMessageTemplate()).isEqualTo("Welcome");
    }

    @Test
    void configureExistingWithAChangedVersionIsAControlledConflict() {
        RegisteredGuildView guild = onboard(GUILD_ID, "configure-stale");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        service.configure(GUILD_ID, "820000000000000002", "Updated");

        assertThatThrownBy(() -> store.configureExisting(
                        guild.registeredGuildId(), "820000000000000003", "Stale", 0))
                .isInstanceOf(GuildWelcomeConflictException.class);
        assertThat(find(guild).getMessageTemplate()).isEqualTo("Updated");
        assertThat(find(guild).getVersion()).isEqualTo(1);
    }

    @Test
    void configureExistingCurrentSnapshotWithIdenticalEnabledValuesIsANoOp() {
        RegisteredGuildView guild = onboard(GUILD_ID, "configure-noop");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(1)));

        StoredGuildWelcome result = store.configureExisting(
                guild.registeredGuildId(), "820000000000000001", "Welcome", 0);

        assertThat(result.version()).isZero();
        assertThat(find(guild).getUpdatedAt()).isEqualTo(INITIAL_INSTANT);
    }

    @Test
    void configureExistingReEnablesADisabledCurrentSnapshot() {
        RegisteredGuildView guild = onboard(GUILD_ID, "configure-reenable");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        GuildWelcomeView disabled = service.disable(GUILD_ID);

        StoredGuildWelcome result = store.configureExisting(
                guild.registeredGuildId(), "820000000000000001", "Welcome", disabled.version());

        assertThat(result.enabled()).isTrue();
        assertThat(result.version()).isEqualTo(disabled.version() + 1);
    }

    @Test
    void disableExistingOnAnAlreadyDisabledCurrentSnapshotIsANoOp() {
        RegisteredGuildView guild = onboard(GUILD_ID, "disable-noop");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        GuildWelcomeView disabled = service.disable(GUILD_ID);
        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(1)));

        StoredGuildWelcome result = store.disableExisting(
                guild.registeredGuildId(), disabled.version());

        assertThat(result.enabled()).isFalse();
        assertThat(result.version()).isEqualTo(disabled.version());
        assertThat(find(guild).getUpdatedAt()).isEqualTo(INITIAL_INSTANT);
    }

    @Test
    void disableExistingAfterAConcurrentChangeIsAControlledConflict() {
        RegisteredGuildView guild = onboard(GUILD_ID, "disable-stale");
        service.configure(GUILD_ID, "820000000000000001", "Welcome");
        long staleVersion = 0;
        service.configure(GUILD_ID, "820000000000000002", "Changed");

        assertThatThrownBy(() -> store.disableExisting(guild.registeredGuildId(), staleVersion))
                .isInstanceOf(GuildWelcomeConflictException.class);
        assertThat(find(guild).isEnabled()).isTrue();
        assertThat(find(guild).getVersion()).isEqualTo(1);
    }

    @Test
    void concurrentStaleUpdatesCannotSilentlyOverwriteEachOther() throws Exception {
        RegisteredGuildView guild = onboard(GUILD_ID, "concurrent-update");
        service.configure(GUILD_ID, "820000000000000001", "Initial");
        CountDownLatch bothLoaded = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> concurrencyFixture.updateAfterSharedLoad(
                    guild.registeredGuildId(),
                    "820000000000000002",
                    "First",
                    INITIAL_INSTANT.plusSeconds(1),
                    bothLoaded));
            Future<?> second = executor.submit(() -> concurrencyFixture.updateAfterSharedLoad(
                    guild.registeredGuildId(),
                    "820000000000000003",
                    "Second",
                    INITIAL_INSTANT.plusSeconds(1),
                    bothLoaded));

            int optimisticFailures = failureCount(first, second);

            assertThat(optimisticFailures).isEqualTo(1);
            GuildWelcomeConfiguration stored = find(guild);
            assertThat(stored.getVersion()).isEqualTo(1);
            assertThat(stored.getMessageTemplate()).isIn("First", "Second");
        } finally {
            executor.shutdownNow();
        }
    }

    private RegisteredGuildView onboard(String discordGuildId, String suffix) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + suffix));
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(discordGuildId).orElseThrow();
        accessFixture.authorizeOwner(operatorIdFor(suffix), guild.registeredGuildId());
        return guild;
    }

    private UUID operatorIdFor(String suffix) {
        return operatorLoginService.login(new OperatorLoginCommand(
                "welcome-op-" + suffix,
                "welcome-" + suffix,
                "Welcome " + suffix,
                null)).operatorId();
    }

    private UUID operator(String suffix) {
        return operatorIdFor(suffix);
    }

    private GuildWelcomeConfiguration find(RegisteredGuildView guild) {
        return repository.findByRegisteredGuildId(guild.registeredGuildId()).orElseThrow();
    }

    private static int failureCount(Future<?>... futures) throws Exception {
        int failures = 0;
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                assertThat(exception.getCause())
                        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
                failures++;
            }
        }
        return failures;
    }

    private void insertRaw(UUID id, UUID registeredGuildId) {
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_welcome_configurations (
                    id, registered_guild_id, enabled, channel_id, message_template,
                    created_at, updated_at, version
                ) VALUES (
                    ?, ?, TRUE, '820000000000000001', 'Welcome',
                    TIMESTAMPTZ '2026-01-02 03:04:05Z',
                    TIMESTAMPTZ '2026-01-02 03:04:05Z',
                    0
                )
                """,
                id,
                registeredGuildId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {

        @Bean
        ConcurrencyFixture guildWelcomeConcurrencyFixture(
                GuildWelcomeConfigurationRepository repository, GuildWelcomeStore store) {
            return new ConcurrencyFixture(repository, store);
        }
    }

    static class ConcurrencyFixture {

        private final GuildWelcomeConfigurationRepository repository;
        private final GuildWelcomeStore store;

        ConcurrencyFixture(GuildWelcomeConfigurationRepository repository, GuildWelcomeStore store) {
            this.repository = repository;
            this.store = store;
        }

        /**
         * Replicates the service's absent-snapshot configure path: each caller confirms an absent
         * snapshot in its own read transaction, waits until both have done so, then races to
         * create. This is intentionally not {@code @Transactional} so {@code store.find} and
         * {@code store.createIfAbsent} each run in their own short transaction, exactly as the
         * service invokes them.
         */
        public String createAfterSharedAbsentSnapshot(
                UUID registeredGuildId,
                String channelId,
                String template,
                CountDownLatch bothSnapshotted) {
            if (store.find(registeredGuildId).isPresent()) {
                throw new IllegalStateException("Expected an absent snapshot before the create race");
            }
            bothSnapshotted.countDown();
            awaitShared(bothSnapshotted, "create race");
            try {
                store.createIfAbsent(registeredGuildId, channelId, template);
                return "success";
            } catch (GuildWelcomeConflictException conflict) {
                return "conflict";
            }
        }

        @Transactional
        public void updateAfterSharedLoad(
                UUID registeredGuildId,
                String channelId,
                String template,
                Instant now,
                CountDownLatch bothLoaded) {
            GuildWelcomeConfiguration configuration =
                    repository.findByRegisteredGuildId(registeredGuildId).orElseThrow();
            bothLoaded.countDown();
            awaitShared(bothLoaded, "shared load");
            configuration.configure(channelId, template, now);
            repository.flush();
        }

        private static void awaitShared(CountDownLatch latch, String phase) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Concurrent operation did not reach the " + phase);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent operation was interrupted", exception);
            }
        }
    }
}
