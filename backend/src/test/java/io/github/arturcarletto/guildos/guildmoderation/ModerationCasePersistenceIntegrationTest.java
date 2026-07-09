package io.github.arturcarletto.guildos.guildmoderation;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.identity.OperatorIdentity;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class
})
class ModerationCasePersistenceIntegrationTest {

    private static final String GUILD_ID = "200000000000001018";
    private static final String TARGET_USER_ID = "300000000000001018";
    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(1, ChronoUnit.HOURS);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GuildModerationCaseRecorder recorder;

    @Autowired
    private ModerationCaseStore store;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private MutableTestClock clock;

    @BeforeEach
    void setUp() {
        clock.setInstant(INSTANT_A);
        jdbcTemplate.update("DELETE FROM guild_os.moderation_cases");
        jdbcTemplate.update("DELETE FROM guild_os.guild_audit_events");
    }

    @Test
    void recordsTimeoutCaseWithSafeMetadataAndAuditEvent() {
        OperatorIdentity operator = operator();
        RegisteredGuildView guild = connectGuild();
        TimeoutMemberCommand command = new TimeoutMemberCommand(
                GUILD_ID,
                TARGET_USER_ID,
                Duration.ofMinutes(15),
                Optional.of("Raw private reason that must never be stored in case history."));

        recorder.recordSuccessfulMemberTimeout(guild.registeredGuildId(), operator.operatorId(), command);

        assertThat(store.find(guild.registeredGuildId(), null, null, null, 50))
                .singleElement()
                .satisfies(moderationCase -> {
                    assertThat(moderationCase.getPublicCaseId()).startsWith("case_");
                    assertThat(moderationCase.getActionType()).isEqualTo(ModerationCaseActionType.MEMBER_TIMEOUT_CREATED);
                    assertThat(moderationCase.getTargetType()).isEqualTo(ModerationCaseTargetType.DISCORD_USER);
                    assertThat(moderationCase.getTargetDiscordUserId()).isEqualTo(TARGET_USER_ID);
                    assertThat(moderationCase.getDurationMinutes()).isEqualTo(15);
                    assertThat(moderationCase.getStatus()).isEqualTo(ModerationCaseStatus.COMPLETED);
                    assertThat(moderationCase.getSummary()).isEqualTo("Member timeout completed.");
                    assertThat(moderationCase.getSummary()).doesNotContain("Raw private reason");
                    assertThat(moderationCase.getOccurredAt()).isEqualTo(INSTANT_A);
                });

        assertThat(jdbcTemplate.queryForList(
                """
                        SELECT operator_id::text || ':' || event_type
                        FROM guild_os.guild_audit_events
                        WHERE registered_guild_id = ?
                        """,
                String.class,
                guild.registeredGuildId()))
                .containsExactly(operator.operatorId() + ":MEMBER_TIMEOUT_CREATED");
    }

    @Test
    void findsCasesNewestFirstWithActionAndTimeFilters() {
        OperatorIdentity operator = operator();
        RegisteredGuildView guild = connectGuild();

        clock.setInstant(INSTANT_A);
        recorder.recordSuccessfulMemberTimeout(guild.registeredGuildId(), operator.operatorId(), command(10));
        clock.setInstant(INSTANT_B);
        recorder.recordSuccessfulMemberTimeout(guild.registeredGuildId(), operator.operatorId(), command(20));

        assertThat(store.find(
                        guild.registeredGuildId(),
                        ModerationCaseActionType.MEMBER_TIMEOUT_CREATED,
                        INSTANT_A,
                        INSTANT_B.plus(1, ChronoUnit.SECONDS),
                        50))
                .extracting(ModerationCase::getDurationMinutes)
                .containsExactly(20, 10);

        assertThat(store.find(
                        guild.registeredGuildId(),
                        ModerationCaseActionType.MEMBER_TIMEOUT_CREATED,
                        INSTANT_B,
                        INSTANT_B.plus(1, ChronoUnit.SECONDS),
                        50))
                .singleElement()
                .extracting(ModerationCase::getDurationMinutes)
                .isEqualTo(20);
    }

    private OperatorIdentity operator() {
        return operatorLoginService.login(new OperatorLoginCommand(
                "case-persistence-op",
                "case-persistence",
                "Case Persistence",
                "avatar"));
    }

    private RegisteredGuildView connectGuild() {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + GUILD_ID));
        return guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
    }

    private static TimeoutMemberCommand command(int durationMinutes) {
        return new TimeoutMemberCommand(
                GUILD_ID,
                TARGET_USER_ID,
                Duration.ofMinutes(durationMinutes),
                Optional.empty());
    }
}
