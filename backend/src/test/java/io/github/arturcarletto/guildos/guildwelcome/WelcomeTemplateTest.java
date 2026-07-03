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
    void templateExceedingBudgetUnderMaximumSanitizedExpansionIsRejected() {
        // The stored template is tiny (72 chars) but each {server} can expand to 200 escaped
        // characters, so nine of them (1800) overflow the administrative preview budget. Under
        // the old synthetic 100-character server context this would have passed validation and
        // only failed later at preview time for a Markdown-heavy guild name.
        assertThatThrownBy(() -> WelcomeTemplate.parse("{server}".repeat(9)))
                .isInstanceOf(InvalidWelcomeTemplateException.class);
    }

    @Test
    void templateAcceptedByParseRendersWithMaximumSanitizedMemberAndServerValues() {
        WelcomeTemplate template = WelcomeTemplate.parse(
                "Welcome {member} to {server} — you are member #{memberCount}! {server}");

        String rendered = WelcomeTemplateRenderer.render(
                template.value(), WelcomePreviewContext.maximum());

        assertThat(rendered)
                .contains("\\_".repeat(WelcomePreviewContext.MAX_RAW_MEMBER_DISPLAY_NAME_LENGTH))
                .contains("\\_".repeat(WelcomePreviewContext.MAX_RAW_SERVER_NAME_LENGTH))
                .contains(Integer.toString(Integer.MAX_VALUE))
                .hasSizeLessThanOrEqualTo(WelcomeTemplateRenderer.DISCORD_MESSAGE_LIMIT);
    }

    @Test
    void ordinaryMarkdownHeavyNamesRenderSuccessfully() {
        WelcomeTemplate template =
                WelcomeTemplate.parse("Welcome {member} to {server}! #{memberCount}");
        // Already-escaped, realistic Markdown-heavy names well within the sanitized bounds.
        WelcomePreviewContext context =
                new WelcomePreviewContext("\\_\\_Artur\\*\\*", "\\~Heaven\\~", 42);

        assertThat(WelcomeTemplateRenderer.render(template.value(), context))
                .isEqualTo("Welcome \\_\\_Artur\\*\\* to \\~Heaven\\~! #42");
    }

    @Test
    void replacementValuesAreNotRecursivelyExpanded() {
        WelcomePreviewContext recursiveLooking =
                new WelcomePreviewContext("{server}", "Heaven", 42);

        assertThat(WelcomeTemplateRenderer.render("Hello {member}", recursiveLooking))
                .isEqualTo("Hello {server}");
    }
}
