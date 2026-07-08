package io.github.arturcarletto.guildos.discordchannel;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.github.arturcarletto.guildos.MutableClockTestConfiguration;
import io.github.arturcarletto.guildos.MutableTestClock;
import io.github.arturcarletto.guildos.TestcontainersConfiguration;
import io.github.arturcarletto.guildos.guild.ConnectGuildCommand;
import io.github.arturcarletto.guildos.guild.GuildConnectionService;
import io.github.arturcarletto.guildos.guild.GuildDirectory;
import io.github.arturcarletto.guildos.guild.RegisteredGuildView;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixture;
import io.github.arturcarletto.guildos.guildaccess.GuildAccessTestFixtureConfiguration;
import io.github.arturcarletto.guildos.identity.AuthenticatedOperator;
import io.github.arturcarletto.guildos.identity.OperatorLoginCommand;
import io.github.arturcarletto.guildos.identity.OperatorLoginService;

import static io.github.arturcarletto.guildos.MutableClockTestConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
@Import({
        TestcontainersConfiguration.class,
        MutableClockTestConfiguration.class,
        GuildAccessTestFixtureConfiguration.class
})
class DiscordGuildChannelHttpIntegrationTest {

    private static final String GUILD_ID = "510000000000000001";
    private static final String TEXT_CHANNEL_ID = "520000000000000001";
    private static final String NEWS_CHANNEL_ID = "520000000000000002";
    private static final String INACTIVE_CHANNEL_ID = "520000000000000003";
    private static final String VOICE_CHANNEL_ID = "520000000000000004";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private DiscordGuildChannelSyncService syncService;

    @Autowired
    private DiscordGuildChannelRepository repository;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clock.setInstant(INITIAL_INSTANT);
        repository.deleteAll();
        repository.flush();
        accessFixture.clear();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void unauthenticatedRequestReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get(channelsUrl(GUILD_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unauthorized\"}"));
    }

    @Test
    void unauthorizedOperatorCannotListAnotherGuildChannels() throws Exception {
        authorize("owner");
        AuthenticatedOperator other = newOperator("other");
        syncService.syncGuildChannels(GUILD_ID, List.of(text(TEXT_CHANNEL_ID, "welcome", 1)));

        mockMvc.perform(get(channelsUrl(GUILD_ID)).with(oauth2Login().oauth2User(other)))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not_found\"}"));
    }

    @Test
    void authorizedOperatorListsOnlyActiveSupportedChannelsWithoutInternalIds() throws Exception {
        AuthenticatedOperator operator = authorize("list");
        syncService.syncGuildChannels(GUILD_ID, List.of(
                text(TEXT_CHANNEL_ID, "welcome", 20),
                news(NEWS_CHANNEL_ID, "announcements", 10),
                text(INACTIVE_CHANNEL_ID, "old-welcome", 30)));
        syncService.syncGuildChannels(GUILD_ID, List.of(
                text(TEXT_CHANNEL_ID, "welcome", 20),
                news(NEWS_CHANNEL_ID, "announcements", 10)));
        insertRawUnsupportedVoiceChannel();

        mockMvc.perform(get(channelsUrl(GUILD_ID)).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.channels", hasSize(2)))
                .andExpect(jsonPath("$.channels[0].discordChannelId").value(NEWS_CHANNEL_ID))
                .andExpect(jsonPath("$.channels[0].name").value("announcements"))
                .andExpect(jsonPath("$.channels[0].type").value("NEWS"))
                .andExpect(jsonPath("$.channels[0].displayName").value("#announcements"))
                .andExpect(jsonPath("$.channels[1].discordChannelId").value(TEXT_CHANNEL_ID))
                .andExpect(jsonPath("$.channels[1].displayName").value("#welcome"))
                .andExpect(jsonPath("$.channels[*].id").doesNotExist())
                .andExpect(jsonPath("$.channels[*].registeredGuildId").doesNotExist())
                .andExpect(jsonPath("$.channels[*].discordGuildId").doesNotExist())
                .andExpect(jsonPath("$.channels[*].active").doesNotExist())
                .andExpect(jsonPath("$.channels[*].lastSyncedAt").doesNotExist());
    }

    @Test
    void syncIsIdempotentUpdatesRenamesAndMarksMissingInactive() {
        authorize("sync");

        syncService.syncGuildChannels(GUILD_ID, List.of(
                text(TEXT_CHANNEL_ID, "welcome", 2),
                news(NEWS_CHANNEL_ID, "announcements", 1)));
        syncService.syncGuildChannels(GUILD_ID, List.of(
                text(TEXT_CHANNEL_ID, "welcome", 2),
                news(NEWS_CHANNEL_ID, "announcements", 1)));

        assertThat(repository.count()).isEqualTo(2);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        syncService.syncGuildChannels(GUILD_ID, List.of(text(TEXT_CHANNEL_ID, "start-here", 3)));

        assertThat(repository.count()).isEqualTo(2);
        DiscordGuildChannel text = repository
                .findByDiscordGuildIdAndDiscordChannelId(GUILD_ID, TEXT_CHANNEL_ID)
                .orElseThrow();
        DiscordGuildChannel news = repository
                .findByDiscordGuildIdAndDiscordChannelId(GUILD_ID, NEWS_CHANNEL_ID)
                .orElseThrow();
        assertThat(text.getName()).isEqualTo("start-here");
        assertThat(text.getPosition()).isEqualTo(3);
        assertThat(text.isActive()).isTrue();
        assertThat(text.getCreatedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(text.getUpdatedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(news.isActive()).isFalse();
        assertThat(news.getLastSyncedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
    }

    @Test
    void invalidGuildIdReturnsBadRequest() throws Exception {
        AuthenticatedOperator operator = authorize("bad-guild");

        mockMvc.perform(get(channelsUrl("not-a-snowflake")).with(oauth2Login().oauth2User(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad_request\"}"));
    }

    private AuthenticatedOperator authorize(String suffix) {
        AuthenticatedOperator operator = newOperator(suffix);
        RegisteredGuildView guild = connectGuild();
        accessFixture.authorizeOwner(operator.operatorId(), guild.registeredGuildId());
        return operator;
    }

    private AuthenticatedOperator newOperator(String suffix) {
        return new AuthenticatedOperator(operatorLoginService.login(new OperatorLoginCommand(
                "channels-op-" + suffix,
                "channels-" + suffix,
                "Channels " + suffix,
                "avatar")));
    }

    private RegisteredGuildView connectGuild() {
        guildConnectionService.connect(new ConnectGuildCommand(GUILD_ID, "Guild " + GUILD_ID));
        return guildDirectory.findByDiscordGuildId(GUILD_ID).orElseThrow();
    }

    private void insertRawUnsupportedVoiceChannel() {
        jdbcTemplate.update(
                """
                INSERT INTO guild_os.discord_guild_channels (
                    id, discord_guild_id, discord_channel_id, name, type, position, active,
                    last_synced_at, created_at, updated_at
                ) VALUES (?, ?, ?, 'voice', 'VOICE', 1, TRUE, ?, ?, ?)
                """,
                UUID.randomUUID(),
                GUILD_ID,
                VOICE_CHANNEL_ID,
                Timestamp.from(INITIAL_INSTANT),
                Timestamp.from(INITIAL_INSTANT),
                Timestamp.from(INITIAL_INSTANT));
    }

    private static DiscordGuildChannelSnapshot text(String channelId, String name, int position) {
        return new DiscordGuildChannelSnapshot(channelId, name, DiscordGuildChannelType.TEXT, position);
    }

    private static DiscordGuildChannelSnapshot news(String channelId, String name, int position) {
        return new DiscordGuildChannelSnapshot(channelId, name, DiscordGuildChannelType.NEWS, position);
    }

    private static String channelsUrl(String discordGuildId) {
        return "/api/v1/guilds/" + discordGuildId + "/channels";
    }
}
