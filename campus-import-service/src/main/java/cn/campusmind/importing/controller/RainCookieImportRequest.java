package cn.campusmind.importing.controller;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RainCookieImportRequest(
        @NotBlank
        String cookie,

        List<String> importScopes,

        Boolean agreeOneTimeUse
) {
}
