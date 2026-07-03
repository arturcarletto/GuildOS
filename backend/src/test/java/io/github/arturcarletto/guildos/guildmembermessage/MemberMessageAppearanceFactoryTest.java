package io.github.arturcarletto.guildos.guildmembermessage;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberMessageAppearanceFactoryTest {

    @Test
    void welcomeCreateAppliesDefaults() {
        MemberMessageAppearance appearance = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME, new Cmd().message("Hi {member}").build());

        assertThat(appearance.title()).isEqualTo("Welcome to {server}!");
        assertThat(appearance.description()).isEqualTo("Hi {member}");
        assertThat(appearance.accentColor()).isEqualTo(0x57F287);
        assertThat(appearance.footer()).isEqualTo("Welcome • {server}");
        assertThat(appearance.mentionMember()).isTrue();
        assertThat(appearance.includeBots()).isFalse();
        assertThat(appearance.imageUrl()).isNull();
        assertThat(appearance.hasButton()).isFalse();
    }

    @Test
    void goodbyeCreateAppliesNeutralDefaultsAndNeverMentions() {
        MemberMessageAppearance appearance = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.GOODBYE, new Cmd().message("Bye {member}").build());

        assertThat(appearance.title()).isEqualTo("A member has left");
        assertThat(appearance.accentColor()).isEqualTo(0xED4245);
        assertThat(appearance.footer()).isEqualTo("Goodbye • {server}");
        assertThat(appearance.mentionMember()).isFalse();
    }

    @Test
    void parsesHexColorWithOrWithoutHashAndRejectsGarbage() {
        assertThat(MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME, new Cmd().message("Hi").color("#FF0000").build())
                .accentColor()).isEqualTo(0xFF0000);
        assertThat(MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME, new Cmd().message("Hi").color("00FF00").build())
                .accentColor()).isEqualTo(0x00FF00);
        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME, new Cmd().message("Hi").color("blue").build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void requiresHttpsForImageAndButtonUrls() {
        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME,
                        new Cmd().message("Hi").image("http://example.com/x.png").build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
        assertThat(MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME,
                        new Cmd().message("Hi").image("https://example.com/x.png").build())
                .imageUrl()).isEqualTo("https://example.com/x.png");
    }

    @Test
    void acceptsAFullButtonPairAndRejectsAHalfPair() {
        MemberMessageAppearance appearance = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME,
                new Cmd().message("Hi").button("Rules", "https://example.com/rules").build());
        assertThat(appearance.hasButton()).isTrue();
        assertThat(appearance.buttonLabel()).isEqualTo("Rules");

        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME,
                        new Cmd().message("Hi").buttonLabelOnly("Rules").build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void goodbyeRejectsMentionAndButton() {
        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.GOODBYE, new Cmd().message("Bye").mention(true).build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.GOODBYE,
                        new Cmd().message("Bye").button("X", "https://e.com/x").build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    @Test
    void updatePreservesOmittedValuesAndOverridesProvidedOnes() {
        MemberMessageAppearance existing = MemberMessageAppearanceFactory.forCreate(
                MemberMessageKind.WELCOME,
                new Cmd().message("Hi").title("Old title").color("#111111").build());

        MemberMessageAppearance updated = MemberMessageAppearanceFactory.forUpdate(
                MemberMessageKind.WELCOME,
                new Cmd().message("Hi again").color("#222222").build(),
                existing);

        assertThat(updated.title()).isEqualTo("Old title");
        assertThat(updated.description()).isEqualTo("Hi again");
        assertThat(updated.accentColor()).isEqualTo(0x222222);
    }

    @Test
    void rejectsWhenCombinedFieldsCouldExceedTheEmbedBudget() {
        // A 1000-char description of {server} placeholders expands far past the 6000 embed budget.
        String hugeDescription = "{server}".repeat(120);
        assertThatThrownBy(() -> MemberMessageAppearanceFactory.forCreate(
                        MemberMessageKind.WELCOME, new Cmd().message(hugeDescription).build()))
                .isInstanceOf(InvalidMemberMessageConfigurationException.class);
    }

    /** Small mutable builder for readable command construction in tests. */
    private static final class Cmd {
        private String channel = "800000000000000001";
        private String message = "Hello {member}";
        private Optional<String> title = Optional.empty();
        private Optional<String> color = Optional.empty();
        private Optional<String> image = Optional.empty();
        private Optional<String> footer = Optional.empty();
        private Optional<Boolean> includeBots = Optional.empty();
        private Optional<Boolean> mention = Optional.empty();
        private Optional<String> buttonLabel = Optional.empty();
        private Optional<String> buttonUrl = Optional.empty();

        Cmd message(String value) {
            this.message = value;
            return this;
        }

        Cmd title(String value) {
            this.title = Optional.of(value);
            return this;
        }

        Cmd color(String value) {
            this.color = Optional.of(value);
            return this;
        }

        Cmd image(String value) {
            this.image = Optional.of(value);
            return this;
        }

        Cmd mention(boolean value) {
            this.mention = Optional.of(value);
            return this;
        }

        Cmd button(String label, String url) {
            this.buttonLabel = Optional.of(label);
            this.buttonUrl = Optional.of(url);
            return this;
        }

        Cmd buttonLabelOnly(String label) {
            this.buttonLabel = Optional.of(label);
            return this;
        }

        ConfigureMemberMessageCommand build() {
            return new ConfigureMemberMessageCommand(
                    channel, message, title, color, image, footer,
                    includeBots, mention, buttonLabel, buttonUrl);
        }
    }
}
