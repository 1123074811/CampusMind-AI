package cn.campusmind.importing.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.importing.application.AuthTokenService;
import cn.campusmind.importing.application.CurrentUser;
import cn.campusmind.importing.application.ImportService;
import cn.campusmind.importing.application.XjuEhallImportService;
import cn.campusmind.importing.domain.ImportTask;
import cn.campusmind.importing.domain.RawDocument;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private final AuthTokenService authTokenService;
    private final ImportService importService;
    private final XjuEhallImportService xjuEhallImportService;

    public ImportController(AuthTokenService authTokenService,
                            ImportService importService,
                            XjuEhallImportService xjuEhallImportService) {
        this.authTokenService = authTokenService;
        this.importService = importService;
        this.xjuEhallImportService = xjuEhallImportService;
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

    @GetMapping("/xju/ehall/config")
    public ApiResponse<XjuEhallConfigResponse> xjuEhallConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(xjuEhallImportService.config());
    }

    @PostMapping("/xju/ehall")
    public ApiResponse<XjuEhallImportResponse> importXjuEhall(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody XjuEhallImportRequest request) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(xjuEhallImportService.importData(user, request));
    }

    @DeleteMapping("/xju/ehall/data")
    public ApiResponse<Map<String, Object>> deleteXjuEhallData(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(xjuEhallImportService.deleteData(user));
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

    @GetMapping("/original/{contentHash}")
    public ResponseEntity<byte[]> downloadOriginal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String contentHash) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        RawDocument document = importService.getOwnedOriginalFile(user, contentHash);
        boolean hasOriginalFile = document.getOriginalFile() != null;
        byte[] body = hasOriginalFile
                ? document.getOriginalFile()
                : document.getPlainText().getBytes(StandardCharsets.UTF_8);
        String storedFileName = document.getOriginalFileName();
        if (!StringUtils.hasText(storedFileName) && document.getOcrMeta() != null
                && document.getOcrMeta().get("fileName") instanceof String legacyFileName) {
            storedFileName = legacyFileName;
        }
        String fileName = safeFileName(storedFileName, hasOriginalFile);
        MediaType contentType = hasOriginalFile
                ? safeMediaType(document.getOriginalContentType())
                : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @DeleteMapping("/tasks/{taskId}/raw")
    public ApiResponse<ImportTaskResponse> deleteRawDocument(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @org.springframework.web.bind.annotation.PathVariable Long taskId) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(importService.deleteRawDocument(user, taskId));
    }

    private static String safeFileName(String originalFileName, boolean hasOriginalFile) {
        String clean = StringUtils.hasText(originalFileName)
                ? StringUtils.getFilename(StringUtils.cleanPath(originalFileName.replace('\r', '_').replace('\n', '_')))
                : "原文";
        if (!hasOriginalFile) {
            int extension = clean.lastIndexOf('.');
            clean = (extension > 0 ? clean.substring(0, extension) : clean) + ".txt";
        }
        return clean;
    }

    private static MediaType safeMediaType(String contentType) {
        try {
            return StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (InvalidMediaTypeException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
