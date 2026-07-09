package cn.campusmind.audit.controller;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long eventId,
        Long operatorId,
        String action,
        String beforeSnapshot,
        String afterSnapshot,
        String comment,
        LocalDateTime createdAt
) {
}
