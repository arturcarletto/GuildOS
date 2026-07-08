package io.github.arturcarletto.guildos.guildmembermessage;

/** Formats the stored 24-bit accent color as a {@code #RRGGBB} hex string for dashboard responses. */
final class MemberMessageColors {

    private MemberMessageColors() {
    }

    static String toHex(int accentColor) {
        return "#%06X".formatted(accentColor & 0xFFFFFF);
    }
}
