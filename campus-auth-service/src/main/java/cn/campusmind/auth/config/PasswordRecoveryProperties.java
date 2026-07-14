package cn.campusmind.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.auth.password-recovery")
public record PasswordRecoveryProperties(
        boolean mailEnabled,
        boolean exposeToken,
        String from,
        String resetUrl,
        long tokenTtlMinutes
) { }
