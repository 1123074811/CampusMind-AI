package cn.campusmind.audit.controller;

import cn.campusmind.audit.application.AdminDashboardService;
import cn.campusmind.audit.application.AiConfigService;
import cn.campusmind.audit.application.AuditTokenService;
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

    public AdminController(AdminDashboardService adminDashboardService,
                           AiConfigService aiConfigService,
                           AuditTokenService auditTokenService) {
        this.adminDashboardService = adminDashboardService;
        this.aiConfigService = aiConfigService;
        this.auditTokenService = auditTokenService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(adminDashboardService.dashboard());
    }

    @GetMapping("/logs")
    public ApiResponse<AdminAuditLogListResponse> logs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(adminDashboardService.auditLogs(action, operatorId, size));
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
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @Valid @RequestBody ReviewEventRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.review(id, operatorId, request.status(), request.comment()));
    }

    @PutMapping("/events/{id}")
    public ApiResponse<AdminEventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.updateEvent(id, request.title(), request.summary(), request.eventType()));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<Void> deleteEvent(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId
    ) {
        adminDashboardService.deleteEvent(id, operatorId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/events/batch-delete")
    public ApiResponse<Void> batchDeleteEvents(
            @RequestBody BatchDeleteRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId
    ) {
        adminDashboardService.batchDeleteEvents(request.ids(), operatorId);
        return ApiResponse.ok(null);
    }
}
