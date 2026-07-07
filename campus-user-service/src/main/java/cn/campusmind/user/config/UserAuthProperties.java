package cn.campusmind.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.jwt")
public record UserAuthProperties(
        String issuer,
        String secret
) {
}
