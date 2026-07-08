package cn.campusmind.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 网关集成测试：启动完整 Spring Cloud Gateway 上下文，通过 WebTestClient
 * 验证鉴权过滤器与异常处理器的端到端行为。
 *
 * <p>测试聚焦鉴权拦截（401）与 actuator 自身端点。鉴权过滤器"放行后转发"的行为
 * 依赖下游服务，由 {@code JwtAuthenticationGlobalFilterTest} 的单元测试覆盖
 * （验证 chain.filter 是否被调用），避免集成测试依赖不可达下游导致超时。
 *
 * <p>测试用路由下游配置在 application-test.yml（指向不可达地址仅占位，放行类用例不在此验证）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void actuatorHealthShouldBeReachableWithoutToken() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedPathWithoutTokenShouldReturnUnauthorizedApiResponse() {
        webTestClient.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("缺少访问令牌");
    }

    @Test
    void protectedPathWithInvalidTokenShouldReturnUnauthorizedApiResponse() {
        webTestClient.get().uri("/api/v1/users/me")
                .header("Authorization", "Bearer not-a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isEqualTo("访问令牌无效或已过期");
    }
}
