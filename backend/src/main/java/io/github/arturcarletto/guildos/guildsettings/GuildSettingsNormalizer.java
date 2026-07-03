package io.github.arturcarletto.guildos.guildsettings;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.Locale;

import org.springframework.util.StringUtils;

final class GuildSettingsNormalizer {

    private static final int MAX_TIMEZONE_LENGTH = 64;
    private static final int MAX_LOCALE_LENGTH = 35;

    private GuildSettingsNormalizer() {
    }

    static NormalizedGuildSettings normalize(String timezone, String localeTag) {
        String normalizedTimezone = normalizeTimezone(timezone);
        String normalizedLocale = normalizeLocale(localeTag);
        return new NormalizedGuildSettings(normalizedTimezone, normalizedLocale);
    }

    private static String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone) || timezone.length() > MAX_TIMEZONE_LENGTH) {
            throw new InvalidGuildSettingsException("timezone must be nonblank and at most 64 characters");
        }
        try {
            String normalized = ZoneId.of(timezone).getId();
            if (normalized.length() > MAX_TIMEZONE_LENGTH) {
                throw new InvalidGuildSettingsException("canonical timezone exceeds 64 characters");
            }
            return normalized;
        }
        catch (DateTimeException exception) {
            throw new InvalidGuildSettingsException("timezone must be a valid ZoneId", exception);
        }
    }

    private static String normalizeLocale(String localeTag) {
        if (!StringUtils.hasText(localeTag) || localeTag.length() > MAX_LOCALE_LENGTH) {
            throw new InvalidGuildSettingsException("locale must be nonblank and at most 35 characters");
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(localeTag).build();
            String language = locale.getLanguage();
            if (!StringUtils.hasText(language) || "und".equalsIgnoreCase(language)) {
                throw new InvalidGuildSettingsException("locale must define a language");
            }
            String normalized = locale.toLanguageTag();
            if (normalized.length() > MAX_LOCALE_LENGTH) {
                throw new InvalidGuildSettingsException("canonical locale exceeds 35 characters");
            }
            return normalized;
        }
        catch (IllformedLocaleException exception) {
            throw new InvalidGuildSettingsException("locale must be a well-formed BCP 47 tag", exception);
        }
    }
}
