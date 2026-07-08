package io.github.arturcarletto.guildos;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorIdentity;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "guildos.discord.enabled=false",
        "guildos.discord.token=",
        "guildos.identity.discord-oauth.enabled=false"
})
@Import({TestcontainersConfiguration.class, FixedClockTestConfiguration.class})
class GuildOsApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

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
        Boolean operatorAccountsTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os' AND table_name = 'operator_accounts'
                        )
                        """,
                Boolean.class);
        Boolean operatorGuildAccessTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os' AND table_name = 'operator_guild_access'
                        )
                        """,
                Boolean.class);
        Boolean guildSettingsTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os' AND table_name = 'guild_settings'
                        )
                """,
                Boolean.class);
        Boolean guildWelcomeConfigurationsTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os'
                              AND table_name = 'guild_welcome_configurations'
                        )
                        """,
                Boolean.class);
        Boolean guildMemberMessageConfigurationsTableExists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = 'guild_os'
                              AND table_name = 'guild_member_message_configurations'
                        )
                        """,
                Boolean.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM guild_os.flyway_schema_history "
                        + "WHERE version IN ('1', '2', '3', '4', '5', '6', '7') AND success",
                Integer.class);
        String guildSettingsDeleteRule = jdbcTemplate.queryForObject(
                """
                        SELECT delete_rule
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = 'guild_os'
                          AND constraint_name = 'guild_settings_registered_guild_fk'
                """,
                String.class);
        String guildMemberMessageDeleteRule = jdbcTemplate.queryForObject(
                """
                        SELECT delete_rule
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = 'guild_os'
                          AND constraint_name = 'guild_member_message_configurations_registered_guild_fk'
                        """,
                String.class);
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
        assertThat(operatorAccountsTableExists).isTrue();
        assertThat(operatorGuildAccessTableExists).isTrue();
        assertThat(guildSettingsTableExists).isTrue();
        // V7 evolved the single-purpose welcome table into the shared member-message table.
        assertThat(guildWelcomeConfigurationsTableExists).isFalse();
        assertThat(guildMemberMessageConfigurationsTableExists).isTrue();
        assertThat(successfulMigrations).isEqualTo(7);
        assertThat(guildSettingsDeleteRule).isEqualTo("NO ACTION");
        assertThat(guildMemberMessageDeleteRule).isEqualTo("NO ACTION");
        assertThat(disconnectedAtType).isEqualTo("timestamp with time zone");
        assertThat(applicationContext.containsBean("discordGateway")).isFalse();
        // Telegram is disabled by default: no poller bean and no polling thread are created.
        assertThat(applicationContext.containsBean("telegramUpdatePoller")).isFalse();
        assertThat(applicationContext.getBeansOfType(ClientRegistrationRepository.class)).isEmpty();
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void currentOperatorReturnsJsonUnauthorizedResponseWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void authenticatedOperatorCanReadOnlyTheSafeCurrentOperatorFields() throws Exception {
        UUID operatorId = UUID.randomUUID();
        AuthenticatedOperator principal = new AuthenticatedOperator(new OperatorIdentity(
                operatorId,
                "discord-user-1",
                "operator",
                "Guild Operator",
                "avatar-hash"));

        mockMvc.perform(get("/api/v1/me").with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(5)))
                .andExpect(jsonPath("$.operatorId").value(operatorId.toString()))
                .andExpect(jsonPath("$.discordUserId").value("discord-user-1"))
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.displayName").value("Guild Operator"))
                .andExpect(jsonPath("$.avatarHash").value("avatar-hash"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
}
