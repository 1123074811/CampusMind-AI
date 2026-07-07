package cn.campusmind.ai.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EventVectorTextRequest(
        @NotBlank
        String title,

        String summary,

        String eventType,

        String startTime,

        String endTime,

        String location,

        List<String> targetScopes,

        List<String> tags,

        @Size(max = 10000)
        String content
) {
}
