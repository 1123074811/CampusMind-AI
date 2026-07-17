package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @Size(max = 128)
        String sessionId,

        @NotBlank
        @Size(max = 1000)
        String message,

        Boolean usePersonalProfile,

        List<@Size(max = 128) String> userScopes
) {
}
