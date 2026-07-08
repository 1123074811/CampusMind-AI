package cn.campusmind.gateway.security;

import cn.campusmind.common.web.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关层异常处理器。
 *
 * <p>campus-common 的 {@code GlobalExceptionHandler} 基于 {@code @RestControllerAdvice}
 * （Servlet 栈），在 Spring Cloud Gateway（WebFlux 响应式）下不生效，因此网关需要
 * 自己实现 {@link WebExceptionHandler}，把鉴权异常转换为统一的 {@link ApiResponse}
 * JSON 响应，保持全站错误响应格式一致。
 *
 * <p>优先级设为 {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}，
 * 确保先于 Spring Boot 默认的 {@code DefaultErrorWebExceptionHandler} 处理鉴权异常。
 */
@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class GatewayWebExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof GatewayAuthenticationException authEx)) {
            // 非鉴权异常，交给后续 handler 处理
            return Mono.error(ex);
        }

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(authEx);
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.fail("UNAUTHORIZED", authEx.getMessage());
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"访问令牌无效或已过期\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
