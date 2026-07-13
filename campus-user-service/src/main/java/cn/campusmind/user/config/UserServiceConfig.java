package cn.campusmind.user.config;

import cn.campusmind.common.config.JwtAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtAuthProperties.class)
public class UserServiceConfig {
}
