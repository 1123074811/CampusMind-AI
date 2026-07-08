package cn.campusmind.importing.controller;

import jakarta.validation.constraints.NotBlank;

public record RainJsonImportRequest(
        @NotBlank
        String dataType,

        @NotBlank
        String rawJson
) {
}
