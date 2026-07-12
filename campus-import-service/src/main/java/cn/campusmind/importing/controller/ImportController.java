package cn.campusmind.importing.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.AuthTokenService;
import cn.campusmind.importing.application.CurrentUser;
import cn.campusmind.importing.application.ImportService;
import cn.campusmind.importing.domain.ImportTask;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @PostMapping("/file")
    public ApiResponse<ImportTaskResponse> importFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.submitFileImport(user, file));
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

    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> listTasks(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        List<ImportTask> tasks = importService.listUserTasks(user.userId(), status, page, Math.min(size, 100));
        List<Map<String, Object>> result = tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", t.getId());
            m.put("importType", t.getImportType());
            m.put("status", t.getTaskStatus());
            m.put("errorMessage", t.getErrorMessage());
            m.put("createdAt", t.getCreatedAt());
            m.put("finishedAt", t.getFinishedAt());
            return m;
        }).toList();
        return ApiResponse.ok(result);
    }
}
