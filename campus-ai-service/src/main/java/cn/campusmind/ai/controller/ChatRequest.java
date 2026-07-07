package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        String sessionId,

        @NotBlank
        @Size(max = 1000)
        String message,

        Boolean usePersonalProfile
) {
}
