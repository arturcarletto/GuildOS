package io.github.arturcarletto.guildos.guildaccess;

import java.math.BigInteger;
import java.util.Objects;

/**
 * A parsed Discord permission bitset.
 *
 * <p>Discord returns permissions as a decimal string that can exceed 64 bits, so the value is held
 * as a {@link BigInteger} and individual permissions are checked by bit index. Only permissions
 * relevant to onboarding eligibility are exposed.
 */
final class DiscordPermissions {

    private static final int ADMINISTRATOR_BIT = 3;
    private static final int MANAGE_GUILD_BIT = 5;

    private final BigInteger bits;

    private DiscordPermissions(BigInteger bits) {
        this.bits = bits;
    }

    static DiscordPermissions of(BigInteger bits) {
        Objects.requireNonNull(bits, "bits must not be null");
        if (bits.signum() < 0) {
            throw new IllegalArgumentException("permissions must not be negative");
        }
        return new DiscordPermissions(bits);
    }

    static DiscordPermissions parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("permissions must not be blank");
        }
        try {
            return of(new BigInteger(raw.trim()));
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("permissions is not a valid integer", exception);
        }
    }

    BigInteger value() {
        return bits;
    }

    boolean hasAdministrator() {
        return bits.testBit(ADMINISTRATOR_BIT);
    }

    boolean hasManageGuild() {
        return bits.testBit(MANAGE_GUILD_BIT);
    }
}
