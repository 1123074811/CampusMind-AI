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
        List<Map<String, Object>> privateEvents,
        List<Map<String, Object>> eventAuditLogs,
        List<Map<String, Object>> dataSourceVersionActions,
        List<Map<String, Object>> submittedInformation,
        List<Map<String, Object>> importTasks,
        List<Map<String, Object>> rawDocuments,
        List<Map<String, Object>> consentHistory,
        List<Map<String, Object>> devices,
        List<Map<String, Object>> notificationDeliveries
) {
    public record Account(
            Long id,
            String username,
            String phone,
            String email,
            String role,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
