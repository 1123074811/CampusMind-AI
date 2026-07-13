package cn.campusmind.gateway;

import cn.campusmind.gateway.config.GatewayAuthProperties;
import cn.campusmind.gateway.config.GatewaySecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 校园事件聚合系统 API 网关。
 *
 * <p>基于 Spring Cloud Gateway（WebFlux 响应式），负责统一 JWT 鉴权、路由转发。
 * 通过 Nacos 服务注册发现实现 {@code lb://} 动态路由，支持多实例负载均衡。
 * 注意：网关不引入 campus-common 的 {@code GlobalExceptionHandler}（基于 Servlet 栈），
 * 鉴权异常由 {@link cn.campusmind.gateway.security.GatewayWebExceptionHandler} 处理。
 */
@SpringBootApplication
@EnableConfigurationProperties({GatewayAuthProperties.class, GatewaySecurityProperties.class})
@EnableDiscoveryClient
public class CampusGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusGatewayApplication.class, args);
    }
}
