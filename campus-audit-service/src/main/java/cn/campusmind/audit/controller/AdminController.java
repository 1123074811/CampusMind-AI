package cn.campusmind.audit.controller;

import cn.campusmind.audit.application.AdminDashboardService;
import cn.campusmind.audit.application.AiConfigService;
import cn.campusmind.audit.application.AuditTokenService;
import cn.campusmind.audit.application.AdminTableService;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final AiConfigService aiConfigService;
    private final AuditTokenService auditTokenService;
    private final AdminTableService adminTableService;

    public AdminController(AdminDashboardService adminDashboardService,
                           AiConfigService aiConfigService,
                           AuditTokenService auditTokenService,
                           AdminTableService adminTableService) {
        this.adminDashboardService = adminDashboardService;
        this.aiConfigService = aiConfigService;
        this.auditTokenService = auditTokenService;
        this.adminTableService = adminTableService;
    }

    @GetMapping("/tables")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> tables(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auditTokenService.requireAdmin(authorization);
        return ApiResponse.ok(adminTableService.tables());
    }

    @GetMapping("/tables/{table}")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> tableRows(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String table,
            @RequestParam(defaultValue = "50") int size) {
        auditTokenService.requireAdmin(authorization);
        return ApiResponse.ok(adminTableService.rows(table, size));
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var operator = auditTokenService.requireOperatorOrAdmin(authorization);
        return ApiResponse.ok(adminDashboardService.dashboard(operator.userId(), operator.role()));
    }

    @GetMapping("/logs")
    public ApiResponse<AdminAuditLogListResponse> logs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "50") int size
    ) {
        var operator = auditTokenService.requireOperatorOrAdmin(authorization);
        Long visibleOperatorId = "ADMIN".equals(operator.role()) ? operatorId : operator.userId();
        return ApiResponse.ok(adminDashboardService.auditLogs(action, visibleOperatorId, size));
    }

    @GetMapping("/ai-config")
    public ApiResponse<AiConfigResponse> aiConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        auditTokenService.requireAdmin(authorization);
        return ApiResponse.ok(aiConfigService.current());
    }

    @PutMapping("/ai-config")
    public ApiResponse<AiConfigResponse> updateAiConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateAiConfigRequest request
    ) {
        return ApiResponse.ok(aiConfigService.update(request, auditTokenService.requireAdmin(authorization)));
    }

    @PutMapping("/events/{id}/review")
    public ApiResponse<AdminEventResponse> review(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ReviewEventRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.review(id,
                auditTokenService.requireOperatorOrAdmin(authorization).userId(), request.status(), request.comment()));
    }

    @PutMapping("/events/{id}")
    public ApiResponse<AdminEventResponse> updateEvent(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        var operator = auditTokenService.requireOperatorOrAdmin(authorization);
        return ApiResponse.ok(adminDashboardService.updateEvent(
                id, operator.userId(), request.title(), request.summary(), request.eventType()));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> deleteEvent(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        adminDashboardService.deleteEvent(id, auditTokenService.requireOperatorOrAdmin(authorization).userId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/events/batch-delete")
    public ApiResponse<Void> batchDeleteEvents(
            @RequestBody BatchDeleteRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        adminDashboardService.batchDeleteEvents(request.ids(), auditTokenService.requireOperatorOrAdmin(authorization).userId());
        return ApiResponse.ok(null);
    }
}
