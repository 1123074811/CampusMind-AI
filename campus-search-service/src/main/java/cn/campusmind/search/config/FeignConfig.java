package cn.campusmind.search.config;

import cn.campusmind.common.feign.FeignAuthRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 客户端公共配置。
 *
 * <p>注册 {@link FeignAuthRequestInterceptor}，在服务间调用时自动透传
 * Authorization、X-User-Id 等请求头，确保下游服务能获取用户身份。
 */
@Configuration
public class FeignConfig {

    @Bean
    public FeignAuthRequestInterceptor feignAuthRequestInterceptor() {
        return new FeignAuthRequestInterceptor();
    }
}
