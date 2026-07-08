package cn.campusmind.importing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.import")
public record ImportProperties(
        String aiBaseUrl,
        int rainCookieTtlMinutes,
        int maxTextLength,
        int maxImageBytes
) {
}
