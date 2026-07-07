package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DecisionPlanRequest(
        @NotBlank
        @Size(max = 1000)
        String query,

        List<String> userScopes,

        Boolean usePersonalProfile
) {
}
