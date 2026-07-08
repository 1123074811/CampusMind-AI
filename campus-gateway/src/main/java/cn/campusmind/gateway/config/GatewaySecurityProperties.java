package cn.campusmind.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关安全相关配置。publicPaths 中的路径前缀无需 JWT 即可访问
 * （如登录、健康检查）。匹配规则：请求路径以任一前缀开头即放行。
 * 对应配置前缀 campus.security。
 */
@ConfigurationProperties(prefix = "campus.security")
public record GatewaySecurityProperties(
        List<String> publicPaths
) {

    public static GatewaySecurityProperties defaults() {
        return new GatewaySecurityProperties(List.of(
                "/api/v1/auth/login",
                "/actuator/health",
                "/actuator/info"
        ));
    }
}
