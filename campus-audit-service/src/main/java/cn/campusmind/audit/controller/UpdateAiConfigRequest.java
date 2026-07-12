package cn.campusmind.audit.controller;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAiConfigRequest(
        @Pattern(regexp = "rule|llm") String mode,
        @Size(max = 512) String baseUrl,
        @Size(max = 128) String model,
        @Size(max = 512) String apiKey
) {
}
