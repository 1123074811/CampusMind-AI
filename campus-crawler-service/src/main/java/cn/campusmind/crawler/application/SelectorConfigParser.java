package cn.campusmind.crawler.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SelectorConfigParser {

    private final ObjectMapper objectMapper;

    public SelectorConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SelectorConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return new SelectorConfig();
        }
        try {
            return objectMapper.readValue(json, SelectorConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid selector_config JSON", e);
        }
    }
}
