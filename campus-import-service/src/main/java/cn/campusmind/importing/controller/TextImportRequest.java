package cn.campusmind.importing.controller;

import jakarta.validation.constraints.NotBlank;

public record TextImportRequest(
        @NotBlank
        String text
) {
}
