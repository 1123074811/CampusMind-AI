package cn.campusmind.ai.controller;

import cn.campusmind.ai.application.AiApplicationService;
import cn.campusmind.ai.config.RuntimeAiConfig;
import cn.campusmind.ai.domain.CampusEventCandidate;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorText;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiApplicationService aiApplicationService;
    private final RuntimeAiConfig runtimeAiConfig;

    public AiController(AiApplicationService aiApplicationService, RuntimeAiConfig runtimeAiConfig) {
        this.aiApplicationService = aiApplicationService;
        this.runtimeAiConfig = runtimeAiConfig;
    }

    @PostMapping("/cognition/extract")
    public ApiResponse<CampusEventCandidate> extract(@Valid @RequestBody CognitionExtractRequest request) {
        return ApiResponse.ok(aiApplicationService.extractEvent(
                request.sourceType(), request.plainText(), request.originalItemId(), request.originalUrl()));
    }

    @PostMapping("/decision/plan")
    public ApiResponse<SearchPlan> plan(@Valid @RequestBody DecisionPlanRequest request) {
        boolean usePersonalProfile = Boolean.TRUE.equals(request.usePersonalProfile());
        return ApiResponse.ok(aiApplicationService.planSearch(request.query(), request.userScopes(), usePersonalProfile));
    }

    @PostMapping("/vector/text")
    public ApiResponse<VectorText> vectorText(@Valid @RequestBody EventVectorTextRequest request) {
        return ApiResponse.ok(aiApplicationService.buildVectorText(request));
    }

    @PostMapping("/vector/store")
    public ApiResponse<VectorStoreResponse> vectorStore(@Valid @RequestBody VectorStoreRequest request) {
        return ApiResponse.ok(aiApplicationService.storeVector(request));
    }

    @PostMapping("/vector/search")
    public ApiResponse<VectorSearchResponse> vectorSearch(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody VectorSearchRequest request) {
        int topK = request.topK() == null ? 10 : request.topK();
        return ApiResponse.ok(aiApplicationService.searchVector(request.query(), topK, userId));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatRequest request) {
        boolean usePersonalProfile = Boolean.TRUE.equals(request.usePersonalProfile());
        return ApiResponse.ok(aiApplicationService.chat(request.sessionId(), request.message(), usePersonalProfile, userId));
    }

    @PutMapping("/runtime-config")
    public ApiResponse<String> reloadConfig(@RequestBody RuntimeConfigRequest request) {
        var mode = runtimeAiConfig.reload(request.mode(), request.baseUrl(), request.model(), request.apiKey());
        return ApiResponse.ok(mode.name().toLowerCase());
    }

    public record RuntimeConfigRequest(String mode, String baseUrl, String model, String apiKey) {}
}
