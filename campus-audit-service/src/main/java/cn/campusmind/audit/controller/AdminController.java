package cn.campusmind.audit.controller;

import cn.campusmind.audit.application.AdminDashboardService;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    public AdminController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(adminDashboardService.dashboard());
    }

    @PutMapping("/events/{id}/review")
    public ApiResponse<AdminEventResponse> review(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId,
            @Valid @RequestBody ReviewEventRequest request
    ) {
        return ApiResponse.ok(adminDashboardService.review(id, operatorId, request.status(), request.comment()));
    }
}
