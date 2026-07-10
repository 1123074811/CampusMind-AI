package cn.campusmind.ai.controller;

import cn.campusmind.ai.application.AiApplicationService;
import cn.campusmind.ai.domain.CampusEventCandidate;
import cn.campusmind.ai.domain.SearchPlan;
import cn.campusmind.ai.domain.VectorText;
import cn.campusmind.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiApplicationService aiApplicationService;

    public AiController(AiApplicationService aiApplicationService) {
        this.aiApplicationService = aiApplicationService;
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
    public ApiResponse<VectorSearchResponse> vectorSearch(@Valid @RequestBody VectorSearchRequest request) {
        int topK = request.topK() == null ? 10 : request.topK();
        return ApiResponse.ok(aiApplicationService.searchVector(request.query(), topK));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        boolean usePersonalProfile = Boolean.TRUE.equals(request.usePersonalProfile());
        return ApiResponse.ok(aiApplicationService.chat(request.sessionId(), request.message(), usePersonalProfile));
    }
}
