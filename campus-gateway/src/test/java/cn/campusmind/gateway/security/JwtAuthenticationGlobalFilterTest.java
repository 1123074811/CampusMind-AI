package cn.campusmind.gateway.security;

import cn.campusmind.gateway.config.GatewayAuthProperties;
import cn.campusmind.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationGlobalFilterTest {

    private static final String ISSUER = "campusmind-auth-test";
    private static final String SECRET = "test-secret-key-with-at-least-32-bytes";

    private JwtAuthenticationGlobalFilter filter;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        GatewayAuthProperties authProperties = new GatewayAuthProperties(ISSUER, SECRET);
        GatewaySecurityProperties securityProperties = new GatewaySecurityProperties(List.of(
                "/api/v1/auth/login",
                "/actuator/health"
        ));
        filter = new JwtAuthenticationGlobalFilter(authProperties, securityProperties);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void publicPathShouldBeReleasedWithoutToken() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void protectedPathWithoutTokenShouldReturnUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(GatewayAuthenticationException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithMalformedAuthorizationHeaderShouldReturnUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(GatewayAuthenticationException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithInvalidTokenShouldReturnUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt")
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(GatewayAuthenticationException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithWrongIssuerTokenShouldReturnUnauthorized() {
        String token = Jwts.builder()
                .issuer("someone-else")
                .subject("1")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(GatewayAuthenticationException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithExpiredTokenShouldReturnUnauthorized() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("1")
                .issuedAt(Date.from(Instant.now().minus(3, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(GatewayAuthenticationException.class)
                .verify();

        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithValidTokenShouldBeReleased() {
        String token = validToken();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    private String validToken() {
        return Jwts.builder()
                .issuer(ISSUER)
                .subject("1")
                .claim("username", "alice")
                .claim("role", "STUDENT")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
                .signWith(signingKey)
                .compact();
    }
}
