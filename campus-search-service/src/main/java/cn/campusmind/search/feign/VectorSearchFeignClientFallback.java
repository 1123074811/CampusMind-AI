package cn.campusmind.search.feign;

import cn.campusmind.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VectorSearchFeignClientFallback implements VectorSearchFeignClient {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchFeignClientFallback.class);

    @Override
    public ApiResponse<VectorSearchResult> search(Map<String, Object> body) {
        log.warn("[SENTINEL-FALLBACK] AI 向量检索服务不可用, query={}", body.get("query"));
        return ApiResponse.fail("VECTOR_SEARCH_DEGRADED", "AI 向量检索服务暂不可用");
    }
}
