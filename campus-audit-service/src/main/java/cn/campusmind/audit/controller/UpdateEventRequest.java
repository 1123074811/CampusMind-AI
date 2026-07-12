package cn.campusmind.audit.controller;

import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
        @Size(max = 512)
        String title,

        @Size(max = 4096)
        String summary,

        @Size(max = 64)
        String eventType
) {
}
