package io.github.arturcarletto.guildos.guildsettings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

record UpdateGuildSettingsRequest(
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 35) String locale,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
