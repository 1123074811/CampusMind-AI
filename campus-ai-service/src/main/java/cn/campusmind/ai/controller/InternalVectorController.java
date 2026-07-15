package cn.campusmind.ai.controller;

import cn.campusmind.ai.application.AiApplicationService;
import cn.campusmind.common.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/ai/vectors")
public class InternalVectorController {

    private final AiApplicationService aiApplicationService;

    public InternalVectorController(AiApplicationService aiApplicationService) {
        this.aiApplicationService = aiApplicationService;
    }

    @DeleteMapping
    public ApiResponse<Void> delete(@RequestBody DeleteVectorsRequest request) {
        aiApplicationService.deleteVectors(request.docIds());
        return ApiResponse.ok(null);
    }

    public record DeleteVectorsRequest(List<String> docIds) {
    }
}
