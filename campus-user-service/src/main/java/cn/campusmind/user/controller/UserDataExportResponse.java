package cn.campusmind.user.controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record UserDataExportResponse(
        Instant exportedAt,
        Account account,
        UserProfileResponse profile,
        List<Map<String, Object>> informationStates,
        List<Map<String, Object>> sourceSubscriptions,
        List<Map<String, Object>> actions,
        List<Map<String, Object>> reminders,
        List<Map<String, Object>> privateEvents
) {
    public record Account(
            Long id,
            String username,
            String phone,
            String role,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
