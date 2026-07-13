package cn.campusmind.search.feign;

import cn.campusmind.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "campus-ai-service", contextId = "vectorSearchFeignClient",
        fallback = VectorSearchFeignClientFallback.class)
public interface VectorSearchFeignClient {

    @PostMapping("/api/v1/ai/vector/search")
    ApiResponse<VectorSearchResult> search(@RequestBody Map<String, Object> body);

    record VectorSearchResult(List<VectorHit> hits, int total) {
    }

    record VectorHit(String docId, String text, double score, Map<String, Object> metadata) {
    }
}
