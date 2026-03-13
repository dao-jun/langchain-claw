package org.monarch.langchain.claw.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ToolCallAuditService {

    private static final int MAX_TEXT_LENGTH = 4_000;

    private final ToolCallAuditRepository repository;
    private final ObjectMapper objectMapper;

    public ToolCallAuditService(ToolCallAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void recordSuccess(ToolCallContext context, String toolName, Map<String, Object> toolInput, String toolOutput, long durationMs) {
        ToolCallAuditEntity entity = new ToolCallAuditEntity();
        populateBaseFields(entity, context, toolName, toolInput, durationMs);
        entity.setSuccess(true);
        entity.setToolOutput(compactText(toolOutput));
        repository.save(entity);
    }

    public void recordFailure(ToolCallContext context, String toolName, Map<String, Object> toolInput, String errorMessage, long durationMs) {
        ToolCallAuditEntity entity = new ToolCallAuditEntity();
        populateBaseFields(entity, context, toolName, toolInput, durationMs);
        entity.setSuccess(false);
        entity.setErrorMessage(compactText(errorMessage));
        repository.save(entity);
    }

    public List<ToolCallAuditView> listRecent(String userId, String sessionId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId, PageRequest.of(0, safeLimit)).stream()
            .map(entity -> new ToolCallAuditView(
                entity.getRequestId(),
                entity.getTraceId(),
                entity.getToolName(),
                entity.isSuccess(),
                entity.getDurationMs(),
                entity.getToolInput(),
                entity.getToolOutput(),
                entity.getErrorMessage(),
                entity.getCreatedAt()))
            .toList();
    }

    private void populateBaseFields(ToolCallAuditEntity entity,
                                    ToolCallContext context,
                                    String toolName,
                                    Map<String, Object> toolInput,
                                    long durationMs) {
        entity.setUserId(context.userId());
        entity.setSessionId(context.sessionId());
        entity.setRequestId(context.requestId());
        entity.setTraceId(context.traceId());
        entity.setToolName(toolName);
        entity.setToolInput(compactJson(toolInput));
        entity.setDurationMs(durationMs);
    }

    private String compactJson(Map<String, Object> toolInput) {
        try {
            return compactText(objectMapper.writeValueAsString(toolInput));
        } catch (JsonProcessingException e) {
            return compactText(String.valueOf(toolInput));
        }
    }

    private String compactText(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }
}
