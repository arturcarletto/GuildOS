package io.github.arturcarletto.guildos.guildsettings;

class InvalidGuildSettingsException extends RuntimeException {

    InvalidGuildSettingsException(String message) {
        super(message);
    }

    InvalidGuildSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
