package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that V7 evolves the single-purpose welcome table into the shared member-message table
 * and migrates existing welcome rows into WELCOME configurations without data loss. It migrates a
 * fresh database only to V6, seeds a welcome row, then runs V7 and inspects the result.
 */
class GuildMemberMessageMigrationIntegrationTest {

    private static PostgreSQLContainer postgres;
    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("guildos")
                .withUsername("guildos_app")
                .withPassword("guildos_test");
        postgres.start();
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(postgres.getJdbcUrl());
        source.setUser(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        dataSource = source;
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void v7PreservesExistingWelcomeConfigurationAsAWelcomeMemberMessage() {
        migrateTo("6");

        UUID guildId = UUID.randomUUID();
        UUID welcomeId = UUID.randomUUID();
        seedGuild(guildId);
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.guild_welcome_configurations (
                    id, registered_guild_id, enabled, channel_id, message_template,
                    created_at, updated_at, version
                ) VALUES (?, ?, FALSE, '820000000000000001', 'Hey {member}!',
                    TIMESTAMPTZ '2026-01-02 03:04:05Z', TIMESTAMPTZ '2026-01-03 03:04:05Z', 4)
                """,
                welcomeId, guildId);

        migrateTo("7");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM guild_os.guild_member_message_configurations WHERE id = ?", welcomeId);

        assertThat(row.get("registered_guild_id")).isEqualTo(guildId);
        assertThat(row.get("message_kind")).isEqualTo("WELCOME");
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("channel_id")).isEqualTo("820000000000000001");
        assertThat(row.get("description_template")).isEqualTo("Hey {member}!");
        assertThat(row.get("title_template")).isEqualTo("Welcome to {server}!");
        assertThat(row.get("accent_color")).isEqualTo(0x57F287);
        assertThat(row.get("mention_member")).isEqualTo(true);
        assertThat(row.get("include_bots")).isEqualTo(false);
        assertThat(row.get("version")).isEqualTo(4L);

        Boolean welcomeTableExists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'guild_os' AND table_name = 'guild_welcome_configurations')
                """,
                Boolean.class);
        assertThat(welcomeTableExists).isFalse();
    }

    private void seedGuild(UUID guildId) {
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.guilds (
                    id, discord_guild_id, guild_name, connection_status,
                    first_connected_at, last_connected_at, created_at, updated_at, version
                ) VALUES (?, '810000000000000001', 'Heaven', 'CONNECTED',
                    NOW(), NOW(), NOW(), NOW(), 0)
                """,
                guildId);
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas("guild_os")
                .defaultSchema("guild_os")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
