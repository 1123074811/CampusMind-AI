package cn.campusmind.importing.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.AuthTokenService;
import cn.campusmind.importing.application.CurrentUser;
import cn.campusmind.importing.application.ImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private final AuthTokenService authTokenService;
    private final ImportService importService;

    public ImportController(AuthTokenService authTokenService, ImportService importService) {
        this.authTokenService = authTokenService;
        this.importService = importService;
    }

    @PostMapping("/text")
    public ApiResponse<ImportTaskResponse> importText(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody TextImportRequest request) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.submitTextImport(user, request));
    }

    @PostMapping("/image")
    public ApiResponse<ImportTaskResponse> importImage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ImageImportRequest request) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.submitImageImport(user, request));
    }

    @PostMapping("/rain/json")
    public ApiResponse<ImportTaskResponse> importRainJson(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RainJsonImportRequest request) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.submitRainJsonImport(user, request));
    }

    @PostMapping("/rain/cookie")
    public ApiResponse<ImportTaskResponse> importRainCookie(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RainCookieImportRequest request) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.submitRainCookieImport(user, request));
    }
}
