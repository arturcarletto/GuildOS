package io.github.arturcarletto.guildos.guildmembermessage;

/**
 * Deterministic sample values used to render a dashboard preview without a live Discord member. The
 * server name uses the guild's already-safe name when available; every other value is a fixed
 * placeholder. The generated mention is a fixed non-routable sample and is never delivered.
 */
final class MemberMessageSamples {

    private static final String SAMPLE_MEMBER = "Sample Member";
    private static final String SAMPLE_USERNAME = "sample_member";
    private static final String SAMPLE_SERVER = "Sample Server";
    private static final int SAMPLE_MEMBER_COUNT = 1234;
    private static final String SAMPLE_MENTION = "<@123456789012345678>";

    private MemberMessageSamples() {
    }

    static MemberMessageRenderContext context(MemberMessageKind kind, String guildName) {
        String server = guildName == null || guildName.isBlank()
                ? SAMPLE_SERVER
                : truncate(guildName, MemberMessageRenderContext.MAX_RAW_SERVER_NAME_LENGTH);
        String mention = kind.supportsMemberMention() ? SAMPLE_MENTION : "";
        return new MemberMessageRenderContext(
                SAMPLE_MEMBER, SAMPLE_USERNAME, server, SAMPLE_MEMBER_COUNT, mention);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
