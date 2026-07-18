package cn.campusmind.event.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class EventVectorLifecycleClient {

    private final RestClient client;

    public EventVectorLifecycleClient(
            @Value("${campus.internal.ai-service-url:http://localhost:8089}") String aiServiceUrl) {
        this.client = RestClient.builder().baseUrl(aiServiceUrl).build();
    }

    public void deleteVectors(List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }
        client.method(org.springframework.http.HttpMethod.DELETE)
                .uri("/internal/ai/vectors")
                .body(Map.of("docIds", docIds))
                .retrieve()
                .toBodilessEntity();
    }
}
