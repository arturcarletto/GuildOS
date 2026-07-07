package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * A templated member-message field, with its maximum stored length, maximum rendered length
 * (the Discord embed limit for that field), and whether it may contain a member mention.
 */
enum MemberMessageField {

    TITLE("title", 256, 256, true),
    DESCRIPTION("description", 1000, 4096, true),
    FOOTER("footer", 256, 2048, true),
    BUTTON_LABEL("button label", 80, 80, false);

    private final String label;
    private final int maxStoredLength;
    private final int maxRenderedLength;
    private final boolean supportsMention;

    MemberMessageField(String label, int maxStoredLength, int maxRenderedLength, boolean supportsMention) {
        this.label = label;
        this.maxStoredLength = maxStoredLength;
        this.maxRenderedLength = maxRenderedLength;
        this.supportsMention = supportsMention;
    }

    String label() {
        return label;
    }

    int maxStoredLength() {
        return maxStoredLength;
    }

    int maxRenderedLength() {
        return maxRenderedLength;
    }

    boolean supportsMention() {
        return supportsMention;
    }
}
