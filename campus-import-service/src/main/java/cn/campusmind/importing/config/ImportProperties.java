package cn.campusmind.importing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.import")
public record ImportProperties(
        String aiBaseUrl,
        String eventBaseUrl,
        String feedBaseUrl,
        int rainCookieTtlMinutes,
        boolean rainCookieEnabled,
        int maxRainJsonBytes,
        int rawDocumentRetentionDays,
        int maxTextLength,
        int maxImageBytes,
        int maxFileBytes,
        /** AI 认知服务连接超时（秒） */
        int aiConnectTimeoutSeconds,
        /** AI 认知服务读取超时（秒） */
        int aiReadTimeoutSeconds,
        /** 单用户每分钟最大导入次数 */
        int rateLimitPerMinute,
        /** Tesseract tessdata 路径 */
        String tessdataPath,
        /** OCR 识别语言（如 chi_sim+eng） */
        String ocrLanguage
) {
}
