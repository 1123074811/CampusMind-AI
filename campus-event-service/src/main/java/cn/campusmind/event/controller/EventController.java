package cn.campusmind.event.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.event.application.EventCommandService;
import cn.campusmind.event.application.EventCommandService.UpsertEventRequest;
import cn.campusmind.event.application.EventQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventQueryService eventQueryService;
    private final EventCommandService eventCommandService;

    public EventController(EventQueryService eventQueryService,
                           EventCommandService eventCommandService) {
        this.eventQueryService = eventQueryService;
        this.eventCommandService = eventCommandService;
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventQueryService.getById(id));
    }

    @GetMapping("/search")
    public ApiResponse<EventSearchResponse> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(eventQueryService.search(eventType, status, keyword, startFrom, startTo, page, size));
    }

    /**
     * 幂等创建事件 + 来源引用，供 import-service 等内部服务调用。
     */
    @PostMapping
    public ApiResponse<Long> createEvent(@RequestBody CreateEventRequest request) {
        UpsertEventRequest req = new UpsertEventRequest(
                request.title(),
                request.summary(),
                request.eventType(),
                request.sourceType(),
                request.startTime(),
                request.endTime(),
                request.location(),
                request.organizer(),
                request.targetScopeJson(),
                request.tagsJson(),
                request.dedupKey(),
                request.rawDocId(),
                request.sourceUrl(),
                request.contentHash()
        );
        Long eventId = eventCommandService.upsertEvent(req);
        return ApiResponse.ok(eventId);
    }

    public record CreateEventRequest(
            String title,
            String summary,
            String eventType,
            String sourceType,
            String startTime,
            String endTime,
            String location,
            String organizer,
            String targetScopeJson,
            String tagsJson,
            String dedupKey,
            String rawDocId,
            String sourceUrl,
            String contentHash
    ) {}
}
