package cn.campusmind.audit.application;

import cn.campusmind.audit.controller.AiConfigResponse;
import cn.campusmind.audit.controller.UpdateAiConfigRequest;
import cn.campusmind.audit.domain.EventAuditLog;
import cn.campusmind.audit.infrastructure.mapper.EventAuditLogMapper;
import cn.campusmind.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    private static final List<String> KEYS = List.of(
            "CAMPUS_AI_MODE", "OPENAI_BASE_URL", "OPENAI_CHAT_MODEL", "OPENAI_API_KEY",
            "SPRING_AI_CHAT_MODEL", "CAMPUS_AI_MODEL_VERSION", "CAMPUS_AI_PROMPT_VERSION"
    );

    private final EventAuditLogMapper eventAuditLogMapper;

    public AiConfigService(EventAuditLogMapper eventAuditLogMapper) {
        this.eventAuditLogMapper = eventAuditLogMapper;
    }

    public AiConfigResponse current() {
        Map<String, String> values = read();
        return response(values);
    }

    public AiConfigResponse update(UpdateAiConfigRequest request, Long operatorId) {
        Map<String, String> values = read();
        String mode = StringUtils.hasText(request.mode()) ? request.mode() : value(values, "CAMPUS_AI_MODE", "rule");
        if ("llm".equals(mode)) {
            validateLlm(request, values);
            values.put("OPENAI_BASE_URL", request.baseUrl().trim());
            values.put("OPENAI_CHAT_MODEL", request.model().trim());
            values.put("SPRING_AI_CHAT_MODEL", "openai");
            values.put("CAMPUS_AI_MODEL_VERSION", request.model().trim());
            values.put("CAMPUS_AI_PROMPT_VERSION", "llm-v1");
            if (StringUtils.hasText(request.apiKey())) values.put("OPENAI_API_KEY", request.apiKey().trim());
        } else {
            values.put("SPRING_AI_CHAT_MODEL", "none");
            values.put("CAMPUS_AI_MODEL_VERSION", "rule-v1");
            values.put("CAMPUS_AI_PROMPT_VERSION", "rule-v1");
        }
        values.put("CAMPUS_AI_MODE", mode);
        write(values);
        writeAudit(operatorId, mode, values.get("OPENAI_BASE_URL"), values.get("OPENAI_CHAT_MODEL"));
        hotReloadAiService(mode, values.get("OPENAI_BASE_URL"), values.get("OPENAI_CHAT_MODEL"), values.get("OPENAI_API_KEY"));
        return response(values);
    }

    private void validateLlm(UpdateAiConfigRequest request, Map<String, String> values) {
        if (!StringUtils.hasText(request.baseUrl()) || !StringUtils.hasText(request.model())) {
            throw invalid("大模型模式需要 Base URL 和模型名");
        }
        try {
            URI uri = URI.create(request.baseUrl().trim());
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) throw invalid("Base URL 必须是 HTTP(S) 地址");
        } catch (IllegalArgumentException ex) {
            throw invalid("Base URL 格式无效");
        }
        if (!StringUtils.hasText(request.apiKey()) && !StringUtils.hasText(values.get("OPENAI_API_KEY"))) {
            throw invalid("请填写 API Key");
        }
    }

    private Map<String, String> read() {
        Map<String, String> values = new LinkedHashMap<>();
        Path path = envPath();
        if (Files.exists(path)) {
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    int split = line.indexOf('=');
                    if (split > 0 && !line.trim().startsWith("#")) values.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            } catch (IOException ex) {
                throw new BusinessException("AI_CONFIG_READ_FAILED", "无法读取智能体配置", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        for (String key : KEYS) values.putIfAbsent(key, System.getenv(key));
        return values;
    }

    private void write(Map<String, String> values) {
        Path path = envPath();
        try {
            List<String> lines = Files.exists(path) ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8)) : new ArrayList<>();
            for (String key : KEYS) replace(lines, key, values.getOrDefault(key, ""));
            Path temp = Files.createTempFile(path.getParent(), ".env", ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new BusinessException("AI_CONFIG_WRITE_FAILED", "保存智能体配置失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static void replace(List<String> lines, String key, String value) {
        String entry = key + "=" + (value == null ? "" : value);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(key + "=")) {
                lines.set(index, entry);
                return;
            }
        }
        lines.add(entry);
    }

    private Path envPath() {
        String override = System.getenv("CAMPUS_ENV_FILE");
        return StringUtils.hasText(override) ? Path.of(override) : Path.of(System.getProperty("user.dir"), ".env");
    }

    private void hotReloadAiService(String mode, String baseUrl, String model, String apiKey) {
        try {
            RestTemplate rest = new RestTemplate();
            Map<String, String> body = new LinkedHashMap<>();
            body.put("mode", mode);
            body.put("baseUrl", baseUrl == null ? "" : baseUrl);
            body.put("model", model == null ? "" : model);
            body.put("apiKey", apiKey == null ? "" : apiKey);
            rest.put("http://localhost:8089/api/v1/ai/runtime-config", body);
            log.info("AI 服务热加载调用成功: mode={}", mode);
        } catch (Exception ex) {
            log.warn("AI 服务热加载调用失败，需重启生效: {}", ex.getMessage());
        }
    }

    private AiConfigResponse response(Map<String, String> values) {
        return new AiConfigResponse(value(values, "CAMPUS_AI_MODE", "rule"), value(values, "OPENAI_BASE_URL", ""),
                value(values, "OPENAI_CHAT_MODEL", ""), StringUtils.hasText(values.get("OPENAI_API_KEY")), false);
    }

    private void writeAudit(Long operatorId, String mode, String baseUrl, String model) {
        EventAuditLog log = new EventAuditLog();
        log.setOperatorId(operatorId);
        log.setAction("AI_CONFIG");
        log.setBeforeSnapshot("{}");
        log.setAfterSnapshot("{\"mode\":\"" + mode + "\",\"baseUrl\":\"" + (baseUrl == null ? "" : baseUrl) + "\",\"model\":\"" + (model == null ? "" : model) + "\"}");
        log.setComment("更新智能体配置（密钥未记录）");
        eventAuditLogMapper.insert(log);
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        return StringUtils.hasText(values.get(key)) ? values.get(key) : fallback;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("AI_CONFIG_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
