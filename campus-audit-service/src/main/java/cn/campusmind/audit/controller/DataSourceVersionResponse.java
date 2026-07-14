package cn.campusmind.audit.controller;

import java.time.LocalDateTime;
import java.util.Map;

public record DataSourceVersionResponse(
        Long id,
        Long sourceId,
        int versionNo,
        String action,
        Map<String, Object> snapshot,
        Long operatorId,
        LocalDateTime createdAt
) { }
