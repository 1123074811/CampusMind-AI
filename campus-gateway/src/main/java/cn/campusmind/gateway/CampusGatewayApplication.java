package cn.campusmind.gateway;

import cn.campusmind.gateway.config.GatewayAuthProperties;
import cn.campusmind.gateway.config.GatewaySecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 校园事件聚合系统 API 网关。
 *
 * <p>基于 Spring Cloud Gateway（WebFlux 响应式），负责统一 JWT 鉴权、路由转发。
 * 注意：网关不引入 campus-common 的 {@code GlobalExceptionHandler}（基于 Servlet 栈），
 * 鉴权异常由 {@link cn.campusmind.gateway.security.GatewayWebExceptionHandler} 处理。
 */
@SpringBootApplication
@EnableConfigurationProperties({GatewayAuthProperties.class, GatewaySecurityProperties.class})
public class CampusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusGatewayApplication.class, args);
    }
}
