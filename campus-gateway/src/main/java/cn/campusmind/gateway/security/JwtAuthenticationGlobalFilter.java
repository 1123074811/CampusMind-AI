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
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

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
    private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR", "STUDENT");

    private final GatewayAuthProperties authProperties;
    private final GatewaySecurityProperties securityProperties;
    private final SecretKey signingKey;
    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationGlobalFilter(GatewayAuthProperties authProperties,
                                         GatewaySecurityProperties securityProperties) {
        this(authProperties, securityProperties, null);
    }

    @Autowired
    public JwtAuthenticationGlobalFilter(GatewayAuthProperties authProperties,
                                         GatewaySecurityProperties securityProperties,
                                         ReactiveStringRedisTemplate redisTemplate) {
        this.authProperties = authProperties;
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        this.signingKey = Keys.hmacShaKeyFor(authProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                })
                .build();
        exchange = exchange.mutate().request(request).build();
        String path = request.getPath().pathWithinApplication().value();

        if (isInternalWriteEndpoint(path, request.getMethod())) {
            return Mono.error(new GatewayAccessDeniedException("该接口仅供内部服务调用"));
        }
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            var accessCookie = request.getCookies().getFirst("campusmind_access");
            if (accessCookie != null && StringUtils.hasText(accessCookie.getValue())) {
                authorization = BEARER_PREFIX + accessCookie.getValue();
            }
        }
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
            String role = claims.get("role", String.class);
            if (!ROLES.contains(role)) {
                throw new GatewayAuthenticationException("访问令牌角色无效");
            }
            requireRouteAccess(path, role);
            ServerHttpRequest authenticatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
                        headers.set("X-User-Id", userId);
                        headers.set("X-User-Role", role);
                    })
                    .build();
            String sessionId = claims.getId();
            Mono<Boolean> revoked;
            if (redisTemplate == null) {
                revoked = Mono.just(false);
            } else {
                Mono<Boolean> sessionRevoked = sessionId == null
                        ? Mono.just(false)
                        : redisTemplate.hasKey("auth:revoked:" + sessionId);
                Mono<Boolean> userRevoked = redisTemplate.opsForValue().get("auth:user-revoked:" + userId)
                        .map(value -> "1".equals(value) || claims.getIssuedAt() == null
                                || claims.getIssuedAt().toInstant().toEpochMilli() <= Long.parseLong(value))
                        .defaultIfEmpty(false);
                revoked = Mono.zip(sessionRevoked, userRevoked).map(flags -> flags.getT1() || flags.getT2());
            }
            ServerWebExchange authenticatedExchange = exchange.mutate().request(authenticatedRequest).build();
            return revoked
                    .onErrorMap(ex -> new GatewayAuthenticationException("会话状态暂时不可用"))
                    .flatMap(isRevoked -> isRevoked
                            ? Mono.error(new GatewayAuthenticationException("会话已注销"))
                            : chain.filter(authenticatedExchange));
        } catch (GatewayAccessDeniedException | GatewayAuthenticationException ex) {
            return Mono.error(ex);
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

    private static void requireRouteAccess(String path, String role) {
        if (path.startsWith("/api/v1/users/admin") && !"ADMIN".equals(role)) {
            throw new GatewayAccessDeniedException("仅管理员可管理用户");
        }
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(role) && !"OPERATOR".equals(role)) {
            throw new GatewayAccessDeniedException("仅管理员或运营可访问后台");
        }
    }

    private static boolean isInternalWriteEndpoint(String path, HttpMethod method) {
        String normalizedPath = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
        if (HttpMethod.POST.equals(method)) {
            return Set.of(
                    "/api/v1/events",
                    "/api/v1/information",
                    "/api/v1/ai/cognition/extract",
                    "/api/v1/ai/decision/plan",
                    "/api/v1/ai/vector/text",
                    "/api/v1/ai/vector/store",
                    "/api/v1/ai/vector/search"
            ).contains(normalizedPath);
        }
        return HttpMethod.PUT.equals(method) && "/api/v1/ai/runtime-config".equals(normalizedPath);
    }
}
