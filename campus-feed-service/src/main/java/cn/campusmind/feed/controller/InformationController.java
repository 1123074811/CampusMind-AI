package cn.campusmind.feed.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.feed.application.InformationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

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
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(informationService.feed(userId, cursor, size));
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
}
