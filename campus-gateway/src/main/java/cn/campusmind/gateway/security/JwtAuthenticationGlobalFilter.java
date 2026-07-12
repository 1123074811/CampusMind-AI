package cn.campusmind.gateway.security;

import cn.campusmind.gateway.config.GatewayAuthProperties;
import cn.campusmind.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 全局 JWT 鉴权过滤器。
 *
 * <p>执行顺序：在 Spring Cloud Gateway 的转发过滤器（NettyRoutingFilter 等）之前
 * 执行，确保未认证请求不会触达下游服务。
 *
 * <p>鉴权策略：
 * <ul>
 *   <li>请求路径命中 {@link GatewaySecurityProperties#publicPaths()} 白名单 → 直接放行</li>
 *   <li>其余请求必须携带合法的 {@code Authorization: Bearer <jwt>} 头</li>
 *   <li>校验失败抛 {@link GatewayAuthenticationException}，由
 *       {@link GatewayWebExceptionHandler} 统一返回 401</li>
 *   <li>校验通过后覆盖下游 {@code X-User-Id}，供未直接解析 JWT 的内部服务做数据隔离</li>
 * </ul>
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 置于 Spring Cloud Gateway 内置转发过滤器之前。
     * NettyRoutingFilter 的 order 为 {@code Ordered.LOWEST_PRECEDENCE - 1}，
     * 这里用 -100 确保鉴权先于转发执行，同时留出空间给其他更高优先级过滤器。
     */
    public static final int ORDER = -100;

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayAuthProperties authProperties;
    private final GatewaySecurityProperties securityProperties;
    private final SecretKey signingKey;

    public JwtAuthenticationGlobalFilter(GatewayAuthProperties authProperties,
                                         GatewaySecurityProperties securityProperties) {
        this.authProperties = authProperties;
        this.securityProperties = securityProperties;
        this.signingKey = Keys.hmacShaKeyFor(authProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return Mono.error(new GatewayAuthenticationException("缺少访问令牌"));
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(authProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            Long.parseLong(userId);
            ServerHttpRequest authenticatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.set("X-User-Id", userId);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(authenticatedRequest).build());
        } catch (RuntimeException ex) {
            return Mono.error(new GatewayAuthenticationException("访问令牌无效或已过期"));
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isPublicPath(String path) {
        if (securityProperties == null || securityProperties.publicPaths() == null) {
            return false;
        }
        for (String prefix : securityProperties.publicPaths()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
