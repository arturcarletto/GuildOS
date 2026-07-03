package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscordPermissionsTest {

    @Test
    void administratorBitIsRecognized() {
        DiscordPermissions permissions = DiscordPermissions.parse("8");

        assertThat(permissions.hasAdministrator()).isTrue();
        assertThat(permissions.hasManageGuild()).isFalse();
    }

    @Test
    void manageGuildBitIsRecognized() {
        DiscordPermissions permissions = DiscordPermissions.parse("32");

        assertThat(permissions.hasManageGuild()).isTrue();
        assertThat(permissions.hasAdministrator()).isFalse();
    }

    @Test
    void combinedPermissionsExposeEachBit() {
        DiscordPermissions permissions = DiscordPermissions.parse("40");

        assertThat(permissions.hasAdministrator()).isTrue();
        assertThat(permissions.hasManageGuild()).isTrue();
    }

    @Test
    void ineligiblePermissionsExposeNeitherBit() {
        DiscordPermissions permissions = DiscordPermissions.parse("1");

        assertThat(permissions.hasAdministrator()).isFalse();
        assertThat(permissions.hasManageGuild()).isFalse();
    }

    @Test
    void parsesBitsetsWiderThanSixtyFourBits() {
        BigInteger wide = BigInteger.ONE.shiftLeft(80).add(BigInteger.valueOf(8));

        DiscordPermissions permissions = DiscordPermissions.parse(wide.toString());

        assertThat(permissions.value()).isEqualTo(wide);
        assertThat(permissions.hasAdministrator()).isTrue();
    }

    @Test
    void rejectsMalformedPermissionValues() {
        assertThatThrownBy(() -> DiscordPermissions.parse("not-a-number"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordPermissions.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordPermissions.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordPermissions.parse("-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
