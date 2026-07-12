package cn.campusmind.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.jwt")
public record AuditAuthProperties(String issuer, String secret) {
}
