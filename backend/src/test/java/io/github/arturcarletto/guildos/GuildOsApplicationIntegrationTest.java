package io.github.arturcarletto.guildos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token="
})
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
class GuildOsApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationStartsAndFlywayCreatesTheApplicationSchema() {
        Boolean schemaExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'guild_os')",
                Boolean.class);
        Boolean guildsTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os' AND table_name = 'guilds'
                        )
                        """,
                Boolean.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guild_os.flyway_schema_history WHERE version IN ('1', '2') AND success",
                Integer.class);
        String disconnectedAtType = jdbcTemplate.queryForObject(
                """
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'guild_os'
                          AND table_name = 'guilds'
                          AND column_name = 'disconnected_at'
                        """,
                String.class);

        assertThat(schemaExists).isTrue();
        assertThat(guildsTableExists).isTrue();
        assertThat(successfulMigrations).isEqualTo(2);
        assertThat(disconnectedAtType).isEqualTo("timestamp with time zone");
        assertThat(applicationContext.containsBean("discordGateway")).isFalse();
    }
}
