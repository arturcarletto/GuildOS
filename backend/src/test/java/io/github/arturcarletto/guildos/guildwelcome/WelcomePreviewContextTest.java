package io.github.arturcarletto.guildos.guildwelcome;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WelcomePreviewContextTest {

    @Test
    void acceptsSanitizedValuesAtTheirDocumentedMaximumLengths() {
        WelcomePreviewContext context = new WelcomePreviewContext(
                "a".repeat(WelcomePreviewContext.MAX_MEMBER_DISPLAY_NAME_LENGTH),
                "b".repeat(WelcomePreviewContext.MAX_SERVER_NAME_LENGTH),
                Integer.MAX_VALUE);

        assertThat(context.memberDisplayName())
                .hasSize(WelcomePreviewContext.MAX_MEMBER_DISPLAY_NAME_LENGTH);
        assertThat(context.serverName())
                .hasSize(WelcomePreviewContext.MAX_SERVER_NAME_LENGTH);
    }

    @Test
    void rejectsMemberDisplayNameBeyondSafeBound() {
        assertThatThrownBy(() -> new WelcomePreviewContext(
                        "a".repeat(WelcomePreviewContext.MAX_MEMBER_DISPLAY_NAME_LENGTH + 1),
                        "Heaven",
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsServerNameBeyondSafeBound() {
        assertThatThrownBy(() -> new WelcomePreviewContext(
                        "Artur",
                        "b".repeat(WelcomePreviewContext.MAX_SERVER_NAME_LENGTH + 1),
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMemberCount() {
        assertThatThrownBy(() -> new WelcomePreviewContext("Artur", "Heaven", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maximumContextMatchesTheWorstCaseEscapedBounds() {
        WelcomePreviewContext maximum = WelcomePreviewContext.maximum();

        assertThat(maximum.memberDisplayName())
                .hasSize(WelcomePreviewContext.MAX_MEMBER_DISPLAY_NAME_LENGTH);
        assertThat(maximum.serverName())
                .hasSize(WelcomePreviewContext.MAX_SERVER_NAME_LENGTH);
        assertThat(maximum.memberCount()).isEqualTo(Integer.MAX_VALUE);
    }
}
