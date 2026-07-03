package io.github.arturcarletto.guildos.guildsettings;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class GuildSettingsHttpIntegrationTest {

    private static final Instant INSTANT_A = MutableClockTestConfiguration.INITIAL_INSTANT;
    private static final Instant INSTANT_B = INSTANT_A.plus(2, ChronoUnit.HOURS);
    private static final String GUILD_ID = "200000000000000123";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private GuildSettingsService service;

    @Autowired
    private GuildSettingsRepository settingsRepository;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private MutableTestClock clock;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.setInstant(INSTANT_A);
        settingsRepository.deleteAll();
        settingsRepository.flush();
        accessFixture.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedGetAndPutReturnJsonUnauthorized() throws Exception {
        mockMvc.perform(get(settingsUrl(GUILD_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));

        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("UTC", "en-US", 0)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void firstAndRepeatedGetReturnDefaultsAndMaterializeOneSafeRow() throws Exception {
        AuthenticatedOperator operator = newOperator("defaults");
        RegisteredGuildView guild = connectGuild(GUILD_ID);
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());

        mockMvc.perform(get(settingsUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(6)))
                .andExpect(jsonPath("$.guildId").value(GUILD_ID))
                .andExpect(jsonPath("$.name").value("Guild " + GUILD_ID))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.locale").value("en-US"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.updatedAt").value(INSTANT_A.toString()))
                .andExpect(jsonPath("$.registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.operatorId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        mockMvc.perform(get(settingsUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));

        assertThat(settingsRepository.count()).isEqualTo(1);
        GuildSettings row = settingsRepository.findAll().get(0);
        assertThat(row.getRegisteredGuildId()).isEqualTo(guild.registeredGuildId());
        assertThat(row.getCreatedAt()).isEqualTo(INSTANT_A);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_A);
    }

    @Test
    void authorizedPutRequiresCsrfAndUpdatesBothCanonicalValues() throws Exception {
        AuthenticatedOperator operator = newOperator("update");
        RegisteredGuildView guild = connectGuild(GUILD_ID);
        accessFixture.authorizeAdmin(operator.operatorId(), guild.registeredGuildId());
        service.get(operator.operatorId(), GUILD_ID);
        clock.setInstant(INSTANT_B);

        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("America/Sao_Paulo", "pt-br", 0)))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}"));

        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("America/Sao_Paulo", "pt-br", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.locale").value("pt-BR"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.updatedAt").value(INSTANT_B.toString()));
    }

    @Test
    void canonicalNoOpPreservesVersionAndUpdatedTimestamp() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("noop", GUILD_ID);
        service.get(operator.operatorId(), GUILD_ID);
        clock.setInstant(INSTANT_B);

        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("UTC", "en-us", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en-US"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.updatedAt").value(INSTANT_A.toString()));

        GuildSettings row = settingsRepository.findAll().get(0);
        assertThat(row.getVersion()).isZero();
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_A);
    }

    @Test
    void staleVersionReturnsConflictWithoutChangingTheRow() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("stale", GUILD_ID);
        service.get(operator.operatorId(), GUILD_ID);
        service.update(operator.operatorId(), GUILD_ID,
                "America/Sao_Paulo", "pt-BR", 0);
        clock.setInstant(INSTANT_B);

        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Europe/London", "en-GB", 0)))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"conflict\"}"));

        GuildSettings row = settingsRepository.findAll().get(0);
        assertThat(row.getTimezone()).isEqualTo("America/Sao_Paulo");
        assertThat(row.getLocaleTag()).isEqualTo("pt-BR");
        assertThat(row.getVersion()).isEqualTo(1);
        assertThat(row.getUpdatedAt()).isEqualTo(INSTANT_A);
    }

    @Test
    void unknownMissingRevokedAndOtherOperatorAccessReturnNotFound() throws Exception {
        AuthenticatedOperator authorized = authorizedOperator("authorized", GUILD_ID);
        AuthenticatedOperator other = newOperator("other");

        assertNotFoundForGet(other, GUILD_ID);
        assertNotFoundForPut(other, GUILD_ID);
        assertNotFoundForGet(other, "299999999999999999");

        service.get(authorized.operatorId(), GUILD_ID);
        UUID registeredGuildId = registeredGuild(GUILD_ID).registeredGuildId();
        accessFixture.revoke(authorized.operatorId(), registeredGuildId);

        assertNotFoundForGet(authorized, GUILD_ID);
        assertNotFoundForPut(authorized, GUILD_ID);
        assertThat(settingsRepository.count()).isEqualTo(1);
    }

    @Test
    void settingsRemainManageableWhileBotIsDisconnected() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("disconnected", GUILD_ID);
        guildConnectionService.disconnect(new DisconnectGuildCommand(GUILD_ID));

        mockMvc.perform(get(settingsUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("UTC"));
        mockMvc.perform(put(settingsUrl(GUILD_ID))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Europe/Paris", "fr-FR", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Paris"));
    }

    @Test
    void invalidInputsReturnStableBadRequestJson() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("validation", GUILD_ID);

        assertBadRequest(operator, GUILD_ID, updateJson("Mars/Olympus", "en-US", 0));
        assertBadRequest(operator, GUILD_ID, updateJson("UTC", "en_US", 0));
        assertBadRequest(operator, GUILD_ID, updateJson("UTC", "und", 0));
        assertBadRequest(operator, GUILD_ID, updateJson(" ", "en-US", 0));
        assertBadRequest(operator, GUILD_ID, updateJson("UTC", " ", 0));
        assertBadRequest(operator, GUILD_ID, updateJson("z".repeat(65), "en-US", 0));
        assertBadRequest(operator, GUILD_ID, updateJson("UTC", "a".repeat(36), 0));
        assertBadRequest(operator, GUILD_ID, "{\"timezone\":\"UTC\",\"locale\":\"en-US\"}");
        assertBadRequest(operator, GUILD_ID, updateJson("UTC", "en-US", -1));
        assertBadRequest(operator, GUILD_ID, "{not-json");

        mockMvc.perform(get(settingsUrl("not-a-snowflake"))
                        .with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    @Test
    void concurrentFirstAccessCreatesExactlyOneSettingsRow() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("first-race", GUILD_ID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<GuildSettingsResponse> first = executor.submit(
                    () -> getAfterSignal(operator.operatorId(), GUILD_ID, ready, start));
            Future<GuildSettingsResponse> second = executor.submit(
                    () -> getAfterSignal(operator.operatorId(), GUILD_ID, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).version()).isZero();
            assertThat(second.get(10, TimeUnit.SECONDS).version()).isZero();
        }
        finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(settingsRepository.count()).isEqualTo(1);
    }

    @Test
    void separatelyAuthorizedOperatorsShareOneSettingsResource() {
        RegisteredGuildView guild = connectGuild(GUILD_ID);
        AuthenticatedOperator first = newOperator("shared-a");
        AuthenticatedOperator second = newOperator("shared-b");
        accessFixture.authorizeOwner(first.operatorId(), guild.registeredGuildId());
        accessFixture.authorizeAdmin(second.operatorId(), guild.registeredGuildId());

        service.get(first.operatorId(), GUILD_ID);
        GuildSettingsResponse updated = service.update(
                second.operatorId(), GUILD_ID, "Asia/Tokyo", "ja-JP", 0);
        GuildSettingsResponse visibleToFirst = service.get(first.operatorId(), GUILD_ID);

        assertThat(updated.version()).isEqualTo(1);
        assertThat(visibleToFirst.timezone()).isEqualTo("Asia/Tokyo");
        assertThat(visibleToFirst.locale()).isEqualTo("ja-JP");
        assertThat(settingsRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentUpdatesWithSameVersionYieldOneSuccessAndOneConflict() throws Exception {
        RegisteredGuildView guild = connectGuild(GUILD_ID);
        AuthenticatedOperator first = newOperator("race-a");
        AuthenticatedOperator second = newOperator("race-b");
        accessFixture.authorizeOwner(first.operatorId(), guild.registeredGuildId());
        accessFixture.authorizeAdmin(second.operatorId(), guild.registeredGuildId());
        service.get(first.operatorId(), GUILD_ID);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = executor.submit(() -> updateAfterSignal(
                    first.operatorId(), "Europe/London", "en-GB", ready, start));
            Future<String> secondResult = executor.submit(() -> updateAfterSignal(
                    second.operatorId(), "Asia/Tokyo", "ja-JP", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "conflict");
        }
        finally {
            start.countDown();
            executor.shutdownNow();
        }

        GuildSettings row = settingsRepository.findAll().get(0);
        assertThat(row.getVersion()).isEqualTo(1);
        assertThat(row.getTimezone()).isIn("Europe/London", "Asia/Tokyo");
    }

    @Test
    void revocationHoldingTheAuthorizationLockPreventsTheFollowingUpdate() throws Exception {
        AuthenticatedOperator operator = authorizedOperator("revoke-race", GUILD_ID);
        UUID registeredGuildId = registeredGuild(GUILD_ID).registeredGuildId();
        service.get(operator.operatorId(), GUILD_ID);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> revocation = executor.submit(() -> accessFixture.revokeAndHold(
                    operator.operatorId(), registeredGuildId, lockAcquired, releaseLock));
            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<String> update = executor.submit(() -> {
                try {
                    service.update(operator.operatorId(), GUILD_ID, "Europe/Rome", "it-IT", 0);
                    return "success";
                }
                catch (GuildSettingsNotFoundException exception) {
                    return "not_found";
                }
            });
            releaseLock.countDown();

            revocation.get(10, TimeUnit.SECONDS);
            assertThat(update.get(10, TimeUnit.SECONDS)).isEqualTo("not_found");
        }
        finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }

        GuildSettings row = settingsRepository.findAll().get(0);
        assertThat(row.getTimezone()).isEqualTo("UTC");
        assertThat(row.getLocaleTag()).isEqualTo("en-US");
        assertThat(row.getVersion()).isZero();
    }

    private AuthenticatedOperator authorizedOperator(String suffix, String discordGuildId) {
        AuthenticatedOperator operator = newOperator(suffix);
        RegisteredGuildView guild = connectGuild(discordGuildId);
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "settings-op-" + suffix,
                "settings-" + suffix,
                "Settings " + suffix,
                "avatar")));
    }

    private RegisteredGuildView connectGuild(String discordGuildId) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + discordGuildId));
        return registeredGuild(discordGuildId);
    }

    private RegisteredGuildView registeredGuild(String discordGuildId) {
        return guildDirectory.findByDiscordGuildId(discordGuildId).orElseThrow();
    }

    private GuildSettingsResponse getAfterSignal(
            UUID operatorId,
            String discordGuildId,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.get(operatorId, discordGuildId);
    }

    private String updateAfterSignal(
            UUID operatorId,
            String timezone,
            String locale,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.update(operatorId, GUILD_ID, timezone, locale, 0);
            return "success";
        }
        catch (GuildSettingsConflictException exception) {
            return "conflict";
        }
    }

    private void assertNotFoundForGet(AuthenticatedOperator operator, String discordGuildId) throws Exception {
        mockMvc.perform(get(settingsUrl(discordGuildId)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    private void assertNotFoundForPut(AuthenticatedOperator operator, String discordGuildId) throws Exception {
        mockMvc.perform(put(settingsUrl(discordGuildId))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("UTC", "en-US", 0)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    private void assertBadRequest(
            AuthenticatedOperator operator, String discordGuildId, String body) throws Exception {
        mockMvc.perform(put(settingsUrl(discordGuildId))
                        .with(oauth2Login().oauth2User(operator))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    private static String settingsUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/settings";
    }

    private static String updateJson(String timezone, String locale, long expectedVersion) {
        return """
                {"timezone":"%s","locale":"%s","expectedVersion":%d}
                """.formatted(timezone, locale, expectedVersion);
    }
}
