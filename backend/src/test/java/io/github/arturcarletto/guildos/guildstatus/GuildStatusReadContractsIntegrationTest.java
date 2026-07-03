package io.github.arturcarletto.guildos.guildstatus;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.arturcarletto.guildos.FixedClockTestConfiguration;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.DisconnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.guildaccess.GuildOnboardingDirectory;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsReader;
import io.github.arturcarletto.guildos.guildsettings.GuildSettingsView;
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
        FixedClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class GuildStatusReadContractsIntegrationTest {

    private static final String DEFAULTS_GUILD_ID = "700000000000000001";
    private static final String SETTINGS_GUILD_ID = "700000000000000002";
    private static final String DISCONNECTED_GUILD_ID = "700000000000000003";

    @Autowired
    private GuildStatusService statusService;

    @Autowired
    private GuildOnboardingDirectory onboardingDirectory;

    @Autowired
    private GuildSettingsReader settingsReader;

    @Autowired
    private GuildConnectionService guildConnectionService;

    @Autowired
    private GuildDirectory guildDirectory;

    @Autowired
    private OperatorLoginService operatorLoginService;

    @Autowired
    private GuildAccessTestFixture accessFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearStatusData() {
        jdbcTemplate.update("DELETE FROM guild_os.guild_settings");
        accessFixture.clear();
    }

    @Test
    void activeOnboardingWithoutSettingsUsesDefaultsWithoutWriting() {
        RegisteredGuildView guild = connect(DEFAULTS_GUILD_ID);
        UUID operatorId = operator("defaults");
        accessFixture.authorizeOwner(operatorId, guild.registeredGuildId());

        assertThat(onboardingDirectory.isOnboarded(DEFAULTS_GUILD_ID)).isTrue();
        assertThat(settingsReader.find(DEFAULTS_GUILD_ID)).isEmpty();
        assertThat(settingsRowCount()).isZero();

        GuildStatusView status = statusService.resolve(DEFAULTS_GUILD_ID);

        assertThat(status).isEqualTo(GuildStatusView.active(
                "Guild " + DEFAULTS_GUILD_ID, "UTC", "en-US", 0));
        assertThat(settingsRowCount()).isZero();

        accessFixture.revoke(operatorId, guild.registeredGuildId());
        assertThat(onboardingDirectory.isOnboarded(DEFAULTS_GUILD_ID)).isFalse();
        assertThat(statusService.resolve(DEFAULTS_GUILD_ID))
                .isEqualTo(GuildStatusView.notOnboarded("Guild " + DEFAULTS_GUILD_ID));
        assertThat(settingsRowCount()).isZero();
    }

    @Test
    void settingsReaderAndStatusReturnOnlyPersistedSafeSettings() {
        RegisteredGuildView guild = connect(SETTINGS_GUILD_ID);
        UUID operatorId = operator("persisted");
        accessFixture.authorizeAdmin(operatorId, guild.registeredGuildId());
        insertSettings(guild.registeredGuildId());

        assertThat(settingsReader.find(SETTINGS_GUILD_ID)).contains(
                new GuildSettingsView("Europe/Paris", "fr-FR", 2));
        assertThat(statusService.resolve(SETTINGS_GUILD_ID)).isEqualTo(GuildStatusView.active(
                "Guild " + SETTINGS_GUILD_ID, "Europe/Paris", "fr-FR", 2));
        assertThat(settingsRowCount()).isEqualTo(1);
    }

    @Test
    void unknownAndDisconnectedGuildsAreUnavailable() {
        assertThat(onboardingDirectory.isOnboarded("799999999999999999")).isFalse();
        assertThat(settingsReader.find("799999999999999999")).isEmpty();
        assertThat(statusService.resolve("799999999999999999"))
                .isEqualTo(GuildStatusView.unavailable());

        RegisteredGuildView guild = connect(DISCONNECTED_GUILD_ID);
        accessFixture.authorizeOwner(operator("disconnected"), guild.registeredGuildId());
        guildConnectionService.disconnect(new DisconnectGuildCommand(DISCONNECTED_GUILD_ID));

        assertThat(statusService.resolve(DISCONNECTED_GUILD_ID))
                .isEqualTo(GuildStatusView.unavailable());
        assertThat(settingsRowCount()).isZero();
    }

    private RegisteredGuildView connect(String discordGuildId) {
        guildConnectionService.connect(new ConnectGuildCommand(discordGuildId, "Guild " + discordGuildId));
        return guildDirectory.findByDiscordGuildId(discordGuildId).orElseThrow();
    }

    private UUID operator(String suffix) {
        return operatorLoginService.login(new OperatorLoginCommand(
                "status-op-" + suffix,
                "status-" + suffix,
                "Status " + suffix,
                null)).operatorId();
    }

    private void insertSettings(UUID registeredGuildId) {
        jdbcTemplate.update(
                """
                        INSERT INTO guild_os.guild_settings (
                            id, registered_guild_id, timezone, locale_tag,
                            created_at, updated_at, version
                        )
                        VALUES (
                            ?, ?, 'Europe/Paris', 'fr-FR',
                            TIMESTAMPTZ '2026-01-02 03:04:05Z',
                            TIMESTAMPTZ '2026-01-02 05:04:05Z',
                            2
                        )
                        """,
                UUID.randomUUID(),
                registeredGuildId);
    }

    private int settingsRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guild_os.guild_settings",
                Integer.class);
    }
}
