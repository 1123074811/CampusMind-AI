package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CognitionExtractRequest(
        @NotBlank
        String sourceType,

        @NotBlank
        @Size(max = 20000)
        String plainText
) {
}
