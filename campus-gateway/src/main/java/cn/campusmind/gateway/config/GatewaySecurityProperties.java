package cn.campusmind.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关安全相关配置。publicPaths 中的路径前缀无需 JWT 即可访问
 * （如登录、健康检查）。匹配规则：请求路径以任一前缀开头即放行。
 * rateLimit 为基于令牌桶的全局限流配置，按客户端 IP 维度限制请求速率。
 * 对应配置前缀 campus.security。
 */
@ConfigurationProperties(prefix = "campus.security")
public record GatewaySecurityProperties(
        List<String> publicPaths,
        RateLimit rateLimit
) {

    /**
     * 网关限流配置。基于 Redis 令牌桶算法，按客户端 IP 维度限流。
     */
    public record RateLimit(
            boolean enabled,
            int capacity,
            int refillTokensPerSecond
    ) {
        public static RateLimit defaults() {
            return new RateLimit(true, 50, 10);
        }
    }

    public static GatewaySecurityProperties defaults() {
        return new GatewaySecurityProperties(
                List.of("/api/v1/auth/login", "/actuator/health", "/actuator/info"),
                RateLimit.defaults()
        );
    }
}
