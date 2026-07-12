package cn.campusmind.importing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.import")
public record ImportProperties(
        String aiBaseUrl,
        String eventBaseUrl,
        int rainCookieTtlMinutes,
        int maxTextLength,
        int maxImageBytes,
        int maxFileBytes,
        /** AI 认知服务连接超时（秒） */
        int aiConnectTimeoutSeconds,
        /** AI 认知服务读取超时（秒） */
        int aiReadTimeoutSeconds,
        /** 单用户每分钟最大导入次数 */
        int rateLimitPerMinute
) {
}
