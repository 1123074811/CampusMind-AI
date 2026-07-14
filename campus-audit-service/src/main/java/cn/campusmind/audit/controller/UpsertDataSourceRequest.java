package cn.campusmind.audit.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpsertDataSourceRequest(
        @NotBlank String name,
        @NotBlank String sourceType,
        @NotBlank @Pattern(regexp = "https?://.+", message = "baseUrl 必须是 HTTP(S) 地址") String baseUrl,
        @Pattern(regexp = "^$|https?://.+", message = "robotsUrl 必须是 HTTP(S) 地址") String robotsUrl,
        @Min(30) @Max(86400) Integer crawlIntervalSeconds,
        @NotBlank String parserType,
        String selectorConfig,
        Boolean enabled
) {
}
