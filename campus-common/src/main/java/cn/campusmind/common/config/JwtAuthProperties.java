package cn.campusmind.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 验签共享配置属性。各服务通过 {@code @EnableConfigurationProperties(JwtAuthProperties.class)}
 * 激活，对应配置前缀 {@code campus.auth.jwt}。
 *
 * <p>替代原先分散在 import-service、search-service、user-service、audit-service 中
 * 各自重复定义的 AuthProperties 类，确保字段定义与配置前缀全局统一。
 *
 * <p>网关（WebFlux）保留独立的 {@code GatewayAuthProperties}，认证服务保留独立的
 * 扩展配置（含 {@code accessTokenTtlMinutes} 等签发参数）。
 */
@ConfigurationProperties(prefix = "campus.auth.jwt")
public record JwtAuthProperties(
        String issuer,
        String secret
) {
}
