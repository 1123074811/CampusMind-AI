package cn.campusmind.importing.controller;

import java.util.Map;

public record ImportTaskResponse(
        Long taskId,
        String status,
        String message,
        Map<String, Object> summary
) {
}
