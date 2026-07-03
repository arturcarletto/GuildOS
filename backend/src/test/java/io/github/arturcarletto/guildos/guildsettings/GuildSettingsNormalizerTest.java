package io.github.arturcarletto.guildos.guildsettings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuildSettingsNormalizerTest {

    @Test
    void normalizesValidTimezoneAndLocale() {
        NormalizedGuildSettings normalized =
                GuildSettingsNormalizer.normalize("America/Sao_Paulo", "pt-br");

        assertThat(normalized.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(normalized.localeTag()).isEqualTo("pt-BR");
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("Mars/Olympus", "en-US"))
                .isInstanceOf(InvalidGuildSettingsException.class);
    }

    @Test
    void rejectsMalformedUndefinedAndLanguageEmptyLocales() {
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("UTC", "en_US"))
                .isInstanceOf(InvalidGuildSettingsException.class);
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("UTC", "und"))
                .isInstanceOf(InvalidGuildSettingsException.class);
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("UTC", "x-private"))
                .isInstanceOf(InvalidGuildSettingsException.class);
    }

    @Test
    void rejectsBlankAndOverlongValues() {
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize(" ", "en-US"))
                .isInstanceOf(InvalidGuildSettingsException.class);
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("UTC", " "))
                .isInstanceOf(InvalidGuildSettingsException.class);
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("z".repeat(65), "en-US"))
                .isInstanceOf(InvalidGuildSettingsException.class);
        assertThatThrownBy(() -> GuildSettingsNormalizer.normalize("UTC", "a".repeat(36)))
                .isInstanceOf(InvalidGuildSettingsException.class);
    }
}
