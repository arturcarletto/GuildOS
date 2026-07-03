package io.github.arturcarletto.guildos.guildwelcome;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WelcomeTemplateTest {

    private static final WelcomePreviewContext CONTEXT =
            new WelcomePreviewContext("Artur", "Heaven", 42);

    @Test
    void rendersSupportedRepeatedPlaceholdersDeterministically() {
        WelcomeTemplate template = WelcomeTemplate.parse(
                "Welcome {member} to {server}! {member}, you are member #{memberCount}.");

        String first = WelcomeTemplateRenderer.render(template.value(), CONTEXT);
        String second = WelcomeTemplateRenderer.render(template.value(), CONTEXT);

        assertThat(first)
                .isEqualTo("Welcome Artur to Heaven! Artur, you are member #42.")
                .isEqualTo(second);
    }

    @Test
    void acceptsStaticTemplates() {
        WelcomeTemplate template = WelcomeTemplate.parse("Welcome to the server!");

        assertThat(WelcomeTemplateRenderer.render(template.value(), CONTEXT))
                .isEqualTo("Welcome to the server!");
    }

    @Test
    void normalizesLineEndingsAndOnlyOuterWhitespace() {
        WelcomeTemplate template = WelcomeTemplate.parse("  \r\nLine one\rLine two\r\n\r\n  ");

        assertThat(template.value()).isEqualTo("Line one\nLine two");
    }

    @Test
    void rejectsBlankAndOversizedTemplates() {
        assertThatThrownBy(() -> WelcomeTemplate.parse(" \r\n "))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("x".repeat(1001)))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
    }

    @Test
    void rejectsUnknownOrMalformedPlaceholders() {
        assertThatThrownBy(() -> WelcomeTemplate.parse("Welcome {username}"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("Welcome {member"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
    }

    @Test
    void rejectsMassAndRawDiscordMentions() {
        assertThatThrownBy(() -> WelcomeTemplate.parse("Hello @everyone"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("Hello @here"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("Hello <@123456789>"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("Hello <@!123456789>"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("Hello <@&123456789>"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
        assertThatThrownBy(() -> WelcomeTemplate.parse("See <#123456789>"))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
    }

    @Test
    void rejectsTemplatesWhoseExpandedPreviewCouldExceedDiscordLimit() {
        assertThatThrownBy(() -> WelcomeTemplate.parse("{server}".repeat(21)))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
    }

    @Test
    void acceptedExpandedPreviewRemainsWithinDiscordLimit() {
        WelcomeTemplate template = WelcomeTemplate.parse("{server}".repeat(16));
        String rendered = WelcomeTemplateRenderer.render(
                template.value(),
                new WelcomePreviewContext("M".repeat(32), "S".repeat(100), Integer.MAX_VALUE));

        assertThat(rendered).hasSizeLessThanOrEqualTo(
                WelcomeTemplateRenderer.DISCORD_MESSAGE_LIMIT);
    }

    @Test
    void replacementValuesAreNotRecursivelyExpanded() {
        WelcomePreviewContext recursiveLooking =
                new WelcomePreviewContext("{server}", "Heaven", 42);

        assertThat(WelcomeTemplateRenderer.render("Hello {member}", recursiveLooking))
                .isEqualTo("Hello {server}");
    }
}
