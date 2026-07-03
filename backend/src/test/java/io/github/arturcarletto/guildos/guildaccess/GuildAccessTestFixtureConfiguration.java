package io.github.arturcarletto.guildos.guildaccess;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class GuildAccessTestFixtureConfiguration {

    @Bean
    GuildAccessTestFixture guildAccessTestFixture(
            OperatorGuildAccessRepository repository,
            OperatorGuildAccessStore store,
            Clock clock) {
        return new GuildAccessTestFixture(repository, store, clock);
    }
}
