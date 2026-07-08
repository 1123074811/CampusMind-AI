package cn.campusmind.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 验签配置，需与 campus-auth-service 签发端保持一致。
 * 对应配置前缀 campus.auth.jwt。
 */
@ConfigurationProperties(prefix = "campus.auth.jwt")
public record GatewayAuthProperties(
        String issuer,
        String secret
) {
}
