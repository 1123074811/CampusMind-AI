package cn.campusmind.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UserAuthProperties.class)
public class UserServiceConfig {
}
