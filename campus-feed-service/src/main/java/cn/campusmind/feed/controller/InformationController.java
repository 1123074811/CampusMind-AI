package cn.campusmind.feed.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.feed.application.InformationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/information")
public class InformationController {

    private final InformationService informationService;

    public InformationController(InformationService informationService) {
        this.informationService = informationService;
    }

    @GetMapping("/feed")
    public ApiResponse<InformationFeedResponse> feed(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Integer cursorSubscriptionMatch,
            @RequestParam(defaultValue = "ALL") String mode,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(informationService.feed(
                userId, cursor, cursorId, cursorSubscriptionMatch, size, mode));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<InformationDetailResponse> detail(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(informationService.detail(userId, id));
    }

    @PutMapping("/items/{id}/read-status")
    public ApiResponse<InformationDetailResponse> updateReadStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody ReadStatusRequest request
    ) {
        return ApiResponse.ok(informationService.updateReadStatus(userId, id, request.readStatus()));
    }

    @PostMapping
    public ApiResponse<Long> createItem(@Valid @RequestBody CreateInformationItemRequest request) {
        return ApiResponse.ok(informationService.createItem(request));
    }

    @DeleteMapping("/rain")
    public ApiResponse<java.util.Map<String, Integer>> deleteRainItems(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(java.util.Map.of(
                "deletedInformationItems", informationService.deleteOwnedRainItems(userId)));
    }

    @DeleteMapping("/rain/{eventId}")
    public ApiResponse<java.util.Map<String, Integer>> deleteRainItem(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long eventId) {
        return ApiResponse.ok(java.util.Map.of(
                "deletedInformationItems", informationService.deleteOwnedRainItem(userId, eventId)));
    }

    @GetMapping("/stats")
    public ApiResponse<UserStatsResponse> stats(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(informationService.stats(userId));
    }

    @GetMapping("/favorites")
    public ApiResponse<InformationFeedResponse> favorites(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.ok(informationService.favorites(userId, size));
    }

    @GetMapping("/read-history")
    public ApiResponse<InformationFeedResponse> readHistory(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ApiResponse.ok(informationService.readHistory(userId, size));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SubscriptionResponse>> subscriptions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(informationService.subscriptions(userId));
    }

    @PutMapping("/subscriptions/{sourceId}")
    public ApiResponse<SubscriptionResponse> updateSubscription(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long sourceId,
            @RequestBody SubscriptionUpdateRequest request
    ) {
        return ApiResponse.ok(informationService.updateSubscription(userId, sourceId, request.enabled()));
    }

    @PostMapping("/items/{id}/actions")
    public ApiResponse<java.util.Map<String, Object>> confirmAction(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id,
            @RequestBody ConfirmActionRequest request) {
        return ApiResponse.ok(informationService.confirmAction(userId, id, request.title()));
    }

    @GetMapping("/actions")
    public ApiResponse<List<ActionItemResponse>> actions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(informationService.actions(userId));
    }

    @PutMapping("/actions/{id}/complete")
    public ApiResponse<java.util.Map<String, Object>> completeAction(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(informationService.completeAction(userId, id));
    }

    @DeleteMapping("/actions/{id}")
    public ApiResponse<java.util.Map<String, Object>> cancelAction(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(informationService.cancelAction(userId, id));
    }

    @GetMapping("/reminders")
    public ApiResponse<List<ReminderItemResponse>> reminders(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ApiResponse.ok(informationService.reminders(userId));
    }

    @PutMapping("/reminders/{id}/dismiss")
    public ApiResponse<ReminderItemResponse> dismissReminder(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(informationService.dismissReminder(userId, id));
    }

    @GetMapping("/items/{id}/related")
    public ApiResponse<List<RelatedItemResponse>> relatedItems(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(informationService.relatedItems(userId, id));
    }

    @GetMapping("/trending")
    public ApiResponse<List<TrendingItemResponse>> trending(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok(informationService.trending(userId, size));
    }

    public record ConfirmActionRequest(String title) {}
}
