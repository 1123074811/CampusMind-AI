package cn.campusmind.feed.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.feed.application.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/information/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    @PutMapping("/devices")
    public ApiResponse<Map<String, Object>> register(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok(notificationService.registerDevice(userId, request.deviceId(), request.platform(), request.pushToken()));
    }

    @DeleteMapping("/devices/{deviceId}")
    public ApiResponse<Void> unregister(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @PathVariable String deviceId) {
        notificationService.unregisterDevice(userId, deviceId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/deliveries")
    public ApiResponse<List<Map<String, Object>>> deliveries(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(notificationService.deliveries(userId));
    }

    @PutMapping("/reminders/{id}/withdraw")
    public ApiResponse<Void> withdraw(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @PathVariable Long id) {
        notificationService.withdraw(userId, id);
        return ApiResponse.ok(null);
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 128) String deviceId,
            @NotBlank @Size(max = 32) String platform,
            @Size(max = 512) String pushToken) { }
}
