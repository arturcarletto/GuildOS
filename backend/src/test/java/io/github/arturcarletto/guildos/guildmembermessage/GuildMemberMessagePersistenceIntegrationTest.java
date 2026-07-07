package io.github.arturcarletto.guildos.guildmembermessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        GuildMemberMessagePersistenceIntegrationTest.ConcurrencyConfiguration.class
})
class GuildMemberMessagePersistenceIntegrationTest {

    private static final String GUILD_ID = "810000000000000001";
    private static final String CHANNEL = "820000000000000001";

    @Autowired
    private GuildMemberMessageService service;

    @Autowired
    private GuildMemberMessageStore store;

    @Autowired
    private GuildMemberMessageConfigurationRepository repository;

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
    void reset() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_member_message_configurations");
        accessFixture.clear();
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void welcomeAndGoodbyeCoexistAsIndependentRowsForTheSameGuild() {
        onboard(GUILD_ID, "coexist");

        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi {member}"));
        service.configure(GUILD_ID, MemberMessageKind.GOODBYE, configure(CHANNEL, "Bye {member}"));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(service.status(GUILD_ID, MemberMessageKind.WELCOME).state())
                .isEqualTo(MemberMessageState.CONFIGURED);
        assertThat(service.status(GUILD_ID, MemberMessageKind.GOODBYE).state())
                .isEqualTo(MemberMessageState.CONFIGURED);
    }

    @Test
    void lifecyclePreservesTimestampsVersionsAndKeepsDisabledEditsDisabled() {
        RegisteredGuildView guild = onboard(GUILD_ID, "lifecycle");

        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi {member}"));
        assertThat(version(guild)).isZero();
        assertThat(updatedAt(guild)).isEqualTo(INITIAL_INSTANT);

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(1)));
        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi {member}"));
        assertThat(version(guild)).as("identical configure is a no-op").isZero();
        assertThat(updatedAt(guild)).isEqualTo(INITIAL_INSTANT);

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(2)));
        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hello {member}"));
        assertThat(version(guild)).isEqualTo(1);
        assertThat(updatedAt(guild)).isEqualTo(INITIAL_INSTANT.plus(Duration.ofMinutes(2)));

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(3)));
        assertThat(service.toggle(GUILD_ID, MemberMessageKind.WELCOME).enabled()).isFalse();
        assertThat(version(guild)).isEqualTo(2);

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(4)));
        GuildMemberMessageView editedWhileDisabled = service.configure(
                GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Welcome back {member}"));
        assertThat(editedWhileDisabled.enabled()).as("editing a disabled config stays disabled").isFalse();
        assertThat(version(guild)).isEqualTo(3);

        assertThat(service.toggle(GUILD_ID, MemberMessageKind.WELCOME).enabled()).isTrue();
        assertThat(version(guild)).isEqualTo(4);
    }

    @Test
    void statusAndPreviewAreReadOnlyAndInvalidTemplatesNeverPersist() {
        onboard(GUILD_ID, "reads");

        assertThat(service.status(GUILD_ID, MemberMessageKind.WELCOME).state())
                .isEqualTo(MemberMessageState.NOT_CONFIGURED);
        assertThat(service.preview(GUILD_ID, MemberMessageKind.WELCOME,
                        new MemberMessageRenderContext("Artur", "artur", "Heaven", 42, "<@1>")).state())
                .isEqualTo(MemberMessageState.NOT_CONFIGURED);
        assertThat(service.toggle(GUILD_ID, MemberMessageKind.WELCOME).state())
                .isEqualTo(MemberMessageState.NOT_CONFIGURED);

        assertThatThrownBy(() -> service.configure(
                        GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi @everyone")))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    void databaseConstraintsRejectInvalidRawRows() {
        RegisteredGuildView guild = onboard(GUILD_ID, "constraints");

        // Goodbye must never mention the member.
        assertThatThrownBy(() -> insertRaw(guild.registeredGuildId(), "GOODBYE", CHANNEL, 0, true, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A button needs both label and URL.
        assertThatThrownBy(() -> insertRaw(guild.registeredGuildId(), "WELCOME", CHANNEL, 0, false, "Label", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Channel ids must be numeric snowflakes.
        assertThatThrownBy(() -> insertRaw(guild.registeredGuildId(), "WELCOME", "not-a-channel", 0, false, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi"));
        // At most one row per guild and kind.
        assertThatThrownBy(() -> insertRaw(guild.registeredGuildId(), "WELCOME", CHANNEL, 0, false, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentInitialConfigureProducesExactlyOneSuccessAndOneControlledConflict() throws Exception {
        RegisteredGuildView guild = onboard(GUILD_ID, "concurrent-initial");
        MemberMessageAppearance one = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME, configure("820000000000000001", "One"));
        MemberMessageAppearance two = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME, configure("820000000000000002", "Two"));
        CountDownLatch snapshotted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrencyFixture.createAfterSharedAbsentSnapshot(
                    guild.registeredGuildId(), MemberMessageKind.WELCOME, "820000000000000001", one, snapshotted));
            Future<String> second = executor.submit(() -> concurrencyFixture.createAfterSharedAbsentSnapshot(
                    guild.registeredGuildId(), MemberMessageKind.WELCOME, "820000000000000002", two, snapshotted));

            List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("success", "conflict");
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            snapshotted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentTogglesResolveToExactlyOneStateTransition() throws Exception {
        RegisteredGuildView guild = onboard(GUILD_ID, "concurrent-toggle");
        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi"));
        CountDownLatch loaded = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> concurrencyFixture.toggleAfterSharedLoad(
                    guild.registeredGuildId(), MemberMessageKind.WELCOME, INITIAL_INSTANT.plusSeconds(1), loaded));
            Future<?> second = executor.submit(() -> concurrencyFixture.toggleAfterSharedLoad(
                    guild.registeredGuildId(), MemberMessageKind.WELCOME, INITIAL_INSTANT.plusSeconds(1), loaded));

            int failures = failureCount(first, second);
            assertThat(failures).isEqualTo(1);
            assertThat(version(guild)).isEqualTo(1);
            assertThat(find(guild).isEnabled()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void storeSnapshotChecksTurnStaleMutationsIntoControlledConflicts() {
        RegisteredGuildView guild = onboard(GUILD_ID, "snapshots");
        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Hi"));

        MemberMessageAppearance appearance = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME, configure(CHANNEL, "Hi"));
        assertThatThrownBy(() -> store.createIfAbsent(
                        guild.registeredGuildId(), MemberMessageKind.WELCOME, CHANNEL, appearance))
                .isInstanceOf(GuildMemberMessageConflictException.class);

        service.configure(GUILD_ID, MemberMessageKind.WELCOME, configure(CHANNEL, "Changed"));
        assertThatThrownBy(() -> store.configureExisting(
                        guild.registeredGuildId(), MemberMessageKind.WELCOME, CHANNEL, appearance, 0L))
                .isInstanceOf(GuildMemberMessageConflictException.class);
        assertThatThrownBy(() -> store.toggleExisting(
                        guild.registeredGuildId(), MemberMessageKind.WELCOME, 0L))
                .isInstanceOf(GuildMemberMessageConflictException.class);
    }

    // ----- helpers -----

    private RegisteredGuildView onboard(String discordGuildId, String suffix) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + suffix));
        RegisteredGuildView guild = guildDirectory.findByDiscordGuildId(discordGuildId).orElseThrow();
        UUID operatorId = operatorLoginService.login(new OperatorLoginCommand(
                "mm-op-" + suffix, "mm-" + suffix, "Member Message " + suffix, null)).operatorId();
        accessFixture.authorizeOwner(operatorId, guild.registeredGuildId());
        return guild;
    }

    private static ConfigureMemberMessageCommand configure(String channelId, String message) {
        return new ConfigureMemberMessageCommand(
                channelId, message,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private GuildMemberMessageConfiguration find(RegisteredGuildView guild) {
        return repository.findByRegisteredGuildIdAndMessageKind(
                guild.registeredGuildId(), MemberMessageKind.WELCOME).orElseThrow();
    }

    private long version(RegisteredGuildView guild) {
        return find(guild).getVersion();
    }

    private Instant updatedAt(RegisteredGuildView guild) {
        return find(guild).getUpdatedAt();
    }

    private void insertRaw(
            UUID registeredGuildId,
            String kind,
            String channelId,
            int accentColor,
            boolean mentionMember,
            String buttonLabel,
            String buttonUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_member_message_configurations (
                    id, registered_guild_id, message_kind, enabled, channel_id, title_template,
                    description_template, accent_color, image_url, footer_template, mention_member,
                    include_bots, button_label, button_url, created_at, updated_at, version
                ) VALUES (?, ?, ?, TRUE, ?, 'Title', 'Body', ?, NULL, 'Footer', ?, FALSE, ?, ?, NOW(), NOW(), 0)
                """,
                UUID.randomUUID(), registeredGuildId, kind, channelId, accentColor, mentionMember,
                buttonLabel, buttonUrl);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {

        @Bean
        ConcurrencyFixture memberMessageConcurrencyFixture(
                GuildMemberMessageConfigurationRepository repository, GuildMemberMessageStore store) {
            return new ConcurrencyFixture(repository, store);
        }
    }

    static class ConcurrencyFixture {

        private final GuildMemberMessageConfigurationRepository repository;
        private final GuildMemberMessageStore store;

        ConcurrencyFixture(
                GuildMemberMessageConfigurationRepository repository, GuildMemberMessageStore store) {
            this.repository = repository;
            this.store = store;
        }

        public String createAfterSharedAbsentSnapshot(
                UUID registeredGuildId,
                MemberMessageKind kind,
                String channelId,
                MemberMessageAppearance appearance,
                CountDownLatch snapshotted) {
            if (store.find(registeredGuildId, kind).isPresent()) {
                throw new IllegalStateException("Expected an absent snapshot before the create race");
            }
            snapshotted.countDown();
            await(snapshotted);
            try {
                store.createIfAbsent(registeredGuildId, kind, channelId, appearance);
                return "success";
            } catch (GuildMemberMessageConflictException conflict) {
                return "conflict";
            }
        }

        @Transactional
        public void toggleAfterSharedLoad(
                UUID registeredGuildId, MemberMessageKind kind, Instant now, CountDownLatch loaded) {
            GuildMemberMessageConfiguration configuration =
                    repository.findByRegisteredGuildIdAndMessageKind(registeredGuildId, kind).orElseThrow();
            loaded.countDown();
            await(loaded);
            configuration.toggle(now);
            repository.flush();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent operation did not reach the shared point");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent operation was interrupted", exception);
            }
        }
    }
}
