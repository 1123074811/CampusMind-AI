package cn.campusmind.importing.controller;

import jakarta.validation.constraints.NotBlank;

public record ImageImportRequest(
        @NotBlank
        String imageBase64,

        String imageName
) {
}
