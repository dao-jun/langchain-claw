package org.monarch.langchain.claw.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class LlmJsonSupport {

    private final ObjectMapper objectMapper;

    public LlmJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize LLM prompt context.", e);
        }
    }

    public <T> T fromJsonObject(String rawResponse, Class<T> targetType) {
        try {
            return objectMapper.readValue(extractFirstJsonObject(rawResponse), targetType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse LLM JSON response: " + rawResponse, e);
        }
    }

    private String extractFirstJsonObject(String rawResponse) {
        if (rawResponse == null) {
            throw new IllegalStateException("LLM response is null.");
        }
        String trimmed = rawResponse.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM response does not contain a JSON object: " + rawResponse);
        }
        return trimmed.substring(start, end + 1);
    }
}
