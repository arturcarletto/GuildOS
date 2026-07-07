package io.github.arturcarletto.guildos.guildmembermessage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberMessageRenderContextTest {

    @Test
    void acceptsSanitizedValuesAtTheirDocumentedMaximumLengths() {
        MemberMessageRenderContext context = new MemberMessageRenderContext(
                "a".repeat(MemberMessageRenderContext.MAX_MEMBER_DISPLAY_NAME_LENGTH),
                "b".repeat(MemberMessageRenderContext.MAX_USERNAME_LENGTH),
                "c".repeat(MemberMessageRenderContext.MAX_SERVER_NAME_LENGTH),
                Integer.MAX_VALUE,
                "<@" + "9".repeat(20) + ">");

        assertThat(context.serverName())
                .hasSize(MemberMessageRenderContext.MAX_SERVER_NAME_LENGTH);
    }

    @Test
    void rejectsValuesBeyondSafeBounds() {
        assertThatThrownBy(() -> new MemberMessageRenderContext(
                        "a".repeat(MemberMessageRenderContext.MAX_MEMBER_DISPLAY_NAME_LENGTH + 1),
                        "b", "c", 1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberMessageRenderContext(
                        "a", "b", "c".repeat(MemberMessageRenderContext.MAX_SERVER_NAME_LENGTH + 1), 1, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberMessageRenderContext(
                        "a", "b", "c", 1, "<@" + "9".repeat(30) + ">"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberMessageRenderContext("a", "b", "c", -1, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maximumContextMatchesWorstCaseBounds() {
        MemberMessageRenderContext maximum = MemberMessageRenderContext.maximum();

        assertThat(maximum.memberDisplayName())
                .hasSize(MemberMessageRenderContext.MAX_MEMBER_DISPLAY_NAME_LENGTH);
        assertThat(maximum.serverName())
                .hasSize(MemberMessageRenderContext.MAX_SERVER_NAME_LENGTH);
        assertThat(maximum.memberCount()).isEqualTo(Integer.MAX_VALUE);
    }
}
