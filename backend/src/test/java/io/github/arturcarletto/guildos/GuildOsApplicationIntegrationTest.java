package io.github.arturcarletto.guildos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GuildOsApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void applicationStartsAndFlywayCreatesTheApplicationSchema() {
        Boolean schemaExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'guild_os')",
                Boolean.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guild_os.flyway_schema_history WHERE version = '1' AND success",
                Integer.class);

        assertThat(schemaExists).isTrue();
        assertThat(successfulMigrations).isEqualTo(1);
    }
}

