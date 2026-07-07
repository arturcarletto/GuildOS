package io.github.arturcarletto.guildos.guildmembermessage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberMessageTemplateTest {

    private static final MemberMessageRenderContext CONTEXT =
            new MemberMessageRenderContext("Artur", "artur_h", "Heaven", 42, "<@100>");

    @Test
    void rendersEveryCommonPlaceholderDeterministicallyAndNonRecursively() {
        MemberMessageTemplate template = MemberMessageTemplate.parse(
                "Hi {member} ({username}) — member #{memberCount} of {server}",
                MemberMessageField.DESCRIPTION,
                MemberMessageKind.WELCOME);

        String rendered = MemberMessageTemplateRenderer.render(template.value(), CONTEXT);

        assertThat(rendered).isEqualTo("Hi Artur (artur_h) — member #42 of Heaven");
    }

    @Test
    void welcomeDescriptionAllowsMentionButGoodbyeRejectsIt() {
        MemberMessageTemplate welcome = MemberMessageTemplate.parse(
                "Welcome {mention}!", MemberMessageField.DESCRIPTION, MemberMessageKind.WELCOME);
        assertThat(MemberMessageTemplateRenderer.render(welcome.value(), CONTEXT))
                .isEqualTo("Welcome <@100>!");

        assertThatThrownBy(() -> MemberMessageTemplate.parse(
                        "Bye {mention}", MemberMessageField.DESCRIPTION, MemberMessageKind.GOODBYE))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class)
                .hasMessageContaining("{mention}");
    }

    @Test
    void buttonLabelNeverAllowsMentionEvenForWelcome() {
        assertThatThrownBy(() -> MemberMessageTemplate.parse(
                        "Say hi {mention}", MemberMessageField.BUTTON_LABEL, MemberMessageKind.WELCOME))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void rejectsUnknownAndMalformedPlaceholders() {
        assertThatThrownBy(() -> MemberMessageTemplate.parse(
                        "Hi {handle}", MemberMessageField.DESCRIPTION, MemberMessageKind.WELCOME))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
        assertThatThrownBy(() -> MemberMessageTemplate.parse(
                        "Hi {member", MemberMessageField.DESCRIPTION, MemberMessageKind.WELCOME))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void rejectsMassAndRawDiscordMentions() {
        for (String bad : new String[] {"Hi @everyone", "Hi @here", "Hi <@123>", "Hi <@&5>", "See <#9>"}) {
            assertThatThrownBy(() -> MemberMessageTemplate.parse(
                            bad, MemberMessageField.DESCRIPTION, MemberMessageKind.WELCOME))
                    .isInstanceOf(InvalidMemberMessageConfigurationException.class);
        }
    }

    @Test
    void rejectsTitleWhoseWorstCaseExpansionExceedsTheTitleLimit() {
        // Each {server} can expand to 200 escaped characters; two exceed the 256 title limit.
        assertThatThrownBy(() -> MemberMessageTemplate.parse(
                        "{server}{server}", MemberMessageField.TITLE, MemberMessageKind.WELCOME))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void acceptedTemplateRendersWithinItsFieldLimitUnderMaximumContext() {
        MemberMessageTemplate template = MemberMessageTemplate.parse(
                "Welcome to {server}", MemberMessageField.TITLE, MemberMessageKind.WELCOME);

        assertThat(MemberMessageTemplate.renderedLength(template.value()))
                .isLessThanOrEqualTo(256);
    }
}
