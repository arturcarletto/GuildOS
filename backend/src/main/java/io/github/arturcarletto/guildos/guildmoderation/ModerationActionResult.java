package io.github.arturcarletto.guildos.guildmoderation;

public record ModerationActionResult(String status) {

    public static ModerationActionResult success() {
        return new ModerationActionResult("SUCCEEDED");
    }
}
