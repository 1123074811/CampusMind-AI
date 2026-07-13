package cn.campusmind.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.search")
public record SearchProperties(
        int defaultTopK,
        int keywordFallbackTopK
) {
}
