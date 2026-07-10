package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CognitionExtractRequest(
        @NotBlank
        String sourceType,

        @NotBlank
        @Size(max = 200000)
        String plainText,

        Long originalItemId,

        @Size(max = 1024)
        String originalUrl
) {
}
