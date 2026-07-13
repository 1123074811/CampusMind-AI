package cn.campusmind.gateway.filter;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.gateway.config.GatewaySecurityProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 基于 Redis 令牌桶的全局限流过滤器。
 *
 * <p>按客户端 IP 维度限制请求速率，防止恶意请求或爬虫压垮后端微服务。
 * 令牌桶参数（容量、填充速率）通过 {@link GatewaySecurityProperties.RateLimit} 配置。
 * 当请求超过限流阈值时返回 HTTP 429 和统一的 {@link ApiResponse} 错误响应。
 *
 * <p>优先级设为 -101，在 {@code JwtAuthenticationGlobalFilter}（-100）之前执行，
 * 确保未认证请求也被限流（防止未登录暴力请求）。
 *
 * <p>Lua 脚本保证令牌桶的原子性操作，在多实例网关环境下也能精确限流。
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);
    public static final int ORDER = -101;

    private final GatewaySecurityProperties securityProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 令牌桶 Lua 脚本：原子性地获取令牌并更新剩余量。
     * KEYS[1] = 限流 key（如 rate_limit:127.0.0.1）
     * ARGV[1] = 令牌桶容量
     * ARGV[2] = 每秒填充速率
     * ARGV[3] = 当前时间戳（秒）
     * ARGV[4] = 请求消耗的令牌数（固定为 1）
     * 返回值：1=允许，0=拒绝
     */
    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            local elapsed = math.max(0, now - last_refill)
            tokens = math.min(capacity, tokens + elapsed * refill_rate)

            if tokens < requested then
                redis.call('HMSET', key, 'tokens', tokens, 'timestamp', now)
                redis.call('EXPIRE', key, 120)
                return 0
            else
                tokens = tokens - requested
                redis.call('HMSET', key, 'tokens', tokens, 'timestamp', now)
                redis.call('EXPIRE', key, 120)
                return 1
            end
            """;

    private final RedisScript<Long> rateLimitScript = RedisScript.of(TOKEN_BUCKET_LUA, Long.class);

    public RateLimitGlobalFilter(GatewaySecurityProperties securityProperties,
                                  ReactiveStringRedisTemplate redisTemplate,
                                  ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewaySecurityProperties.RateLimit config = securityProperties.rateLimit();
        if (config == null || !config.enabled()) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange.getRequest());
        String key = "rate_limit:" + clientIp;
        String now = String.valueOf(System.currentTimeMillis() / 1000);

        return redisTemplate.execute(rateLimitScript,
                        List.of(key),
                        String.valueOf(config.capacity()),
                        String.valueOf(config.refillTokensPerSecond()),
                        now,
                        "1")
                .next()
                .onErrorResume(ex -> {
                    log.warn("限流 Redis 异常，降级放行: {}", ex.getMessage());
                    return Mono.just(1L);
                })
                .flatMap(allowed -> {
                    if (allowed != null && allowed == 1L) {
                        return chain.filter(exchange);
                    }
                    return rejectWithTooManyRequests(exchange);
                });
    }

    static String extractClientIp(ServerHttpRequest request) {
        var remoteAddress = request.getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> rejectWithTooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.fail("RATE_LIMITED", "请求过于频繁，请稍后重试");
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"success\":false,\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
