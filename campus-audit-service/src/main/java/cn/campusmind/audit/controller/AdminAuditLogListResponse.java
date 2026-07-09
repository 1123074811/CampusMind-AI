package cn.campusmind.audit.controller;

import java.util.List;

public record AdminAuditLogListResponse(
        List<AdminAuditLogResponse> items,
        long total
) {
}
