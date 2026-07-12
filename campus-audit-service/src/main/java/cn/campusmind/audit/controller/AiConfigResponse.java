package cn.campusmind.audit.controller;

public record AiConfigResponse(
        String mode,
        String baseUrl,
        String model,
        boolean apiKeyConfigured,
        boolean restartRequired
) {
}
