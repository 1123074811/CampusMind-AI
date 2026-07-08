package cn.campusmind.importing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.jwt")
public record AuthProperties(
        String issuer,
        String secret
) {
}
