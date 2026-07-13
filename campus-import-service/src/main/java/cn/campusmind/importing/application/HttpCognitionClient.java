package cn.campusmind.importing.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.feign.CognitionFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 认知客户端适配器：通过 Feign 调用 campus-ai-service 的认知抽取接口。
 *
 * <p>底层使用 {@link CognitionFeignClient}（基于 OpenFeign + Nacos 服务发现），
 * 替代原先的 RestClient 硬编码 URL 方式。请求头透传由
 * {@link cn.campusmind.common.feign.FeignAuthRequestInterceptor} 自动处理。
 * 测试中可通过 @MockBean CognitionClient 替换，避免依赖真实 ai-service 运行。
 */
@Component
public class HttpCognitionClient implements CognitionClient {

    private final CognitionFeignClient feignClient;

    public HttpCognitionClient(CognitionFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public CognitionResult extract(String sourceType, String plainText) {
        ApiResponse<CognitionResult> response;
        try {
            response = feignClient.extract(
                    Map.of("sourceType", sourceType, "plainText", plainText));
        } catch (RuntimeException ex) {
            throw new BusinessException("COGNITION_UNAVAILABLE",
                    "AI 认知服务暂不可用：" + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        if (response == null || !response.success() || response.data() == null) {
            String message = response == null ? "无响应" : response.message();
            throw new BusinessException("COGNITION_FAILED",
                    "AI 认知抽取失败：" + message, HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}
