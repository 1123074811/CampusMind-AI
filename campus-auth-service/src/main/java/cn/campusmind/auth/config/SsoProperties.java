package cn.campusmind.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.sso")
public record SsoProperties(String callbackUrl) {
}
