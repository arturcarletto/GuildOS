package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordGuildEligibilityTest {

    private static OperatorDiscordGuild guild(boolean owner, String permissions) {
        return new OperatorDiscordGuild("123", "Example", "icon", owner, new BigInteger(permissions));
    }

    @Test
    void ownerIsEligibleAsOwner() {
        OperatorDiscordGuild guild = guild(true, "0");

        assertThat(DiscordGuildEligibility.isEligible(guild)).isTrue();
        assertThat(DiscordGuildEligibility.roleOf(guild)).isEqualTo(GuildAccessRole.OWNER);
    }

    @Test
    void administratorIsEligibleAsAdmin() {
        OperatorDiscordGuild guild = guild(false, "8");

        assertThat(DiscordGuildEligibility.isEligible(guild)).isTrue();
        assertThat(DiscordGuildEligibility.roleOf(guild)).isEqualTo(GuildAccessRole.ADMIN);
    }

    @Test
    void manageGuildIsEligibleAsAdmin() {
        OperatorDiscordGuild guild = guild(false, "32");

        assertThat(DiscordGuildEligibility.isEligible(guild)).isTrue();
        assertThat(DiscordGuildEligibility.roleOf(guild)).isEqualTo(GuildAccessRole.ADMIN);
    }

    @Test
    void withoutOwnershipOrPrivilegedPermissionsIsNotEligible() {
        OperatorDiscordGuild guild = guild(false, "1");

        assertThat(DiscordGuildEligibility.isEligible(guild)).isFalse();
    }
}
