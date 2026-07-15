package cn.campusmind.user.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class UserDataLifecycleClient {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() { };

    private final RestClient importClient;
    private final RestClient aiClient;
    private final ObjectMapper objectMapper;

    public UserDataLifecycleClient(
            @Value("${campus.internal.import-service-url:http://localhost:8085}") String importServiceUrl,
            @Value("${campus.internal.ai-service-url:http://localhost:8089}") String aiServiceUrl,
            ObjectMapper objectMapper) {
        this.importClient = RestClient.builder().baseUrl(importServiceUrl).build();
        this.aiClient = RestClient.builder().baseUrl(aiServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listRawDocuments(Long userId) {
        JsonNode response = importClient.get()
                .uri("/internal/import/users/{userId}/raw-documents", userId)
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = response == null ? null : response.get("data");
        return data == null || !data.isArray() ? List.of() : objectMapper.convertValue(data, MAP_LIST);
    }

    public void deleteRawDocuments(Long userId) {
        importClient.delete()
                .uri("/internal/import/users/{userId}/raw-documents", userId)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteVectors(List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }
        aiClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/internal/ai/vectors")
                .body(Map.of("docIds", docIds))
                .retrieve()
                .toBodilessEntity();
    }
}
