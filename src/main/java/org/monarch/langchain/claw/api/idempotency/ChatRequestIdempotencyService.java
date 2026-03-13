package org.monarch.langchain.claw.api.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.monarch.langchain.claw.api.dto.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatRequestIdempotencyService {

    private final ChatRequestRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Object> requestLocks = new ConcurrentHashMap<>();

    public ChatRequestIdempotencyService(ChatRequestRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ChatResponse execute(String userId,
                                String requestId,
                                String requestFingerprint,
                                Supplier<ChatResponse> responseSupplier) {
        Optional<ChatResponse> cached = findCachedResponse(userId, requestId, requestFingerprint);
        if (cached.isPresent()) {
            return cached.get();
        }

        String lockKey = userId + "::" + requestId;
        Object lock = requestLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                cached = findCachedResponse(userId, requestId, requestFingerprint);
                if (cached.isPresent()) {
                    return cached.get();
                }

                ChatResponse response = responseSupplier.get();
                saveRecord(userId, requestId, requestFingerprint, response);
                return response;
            } finally {
                requestLocks.remove(lockKey, lock);
            }
        }
    }

    public String createFingerprint(String requestedSessionId, String message) {
        Map<String, Object> fingerprintPayload = new LinkedHashMap<>();
        fingerprintPayload.put("sessionId", requestedSessionId == null ? "" : requestedSessionId);
        fingerprintPayload.put("message", message);
        try {
            return objectMapper.writeValueAsString(fingerprintPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat request fingerprint.", e);
        }
    }

    private Optional<ChatResponse> findCachedResponse(String userId, String requestId, String requestFingerprint) {
        return repository.findByUserIdAndRequestId(userId, requestId)
            .map(record -> {
                validateFingerprint(record, requestFingerprint);
                return deserializeResponse(record.getResponseJson());
            });
    }

    private void validateFingerprint(ChatRequestRecordEntity record, String requestFingerprint) {
        if (!Objects.equals(record.getRequestFingerprint(), requestFingerprint)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "相同的 X-Request-Id 已被用于不同的聊天请求，请为新请求使用新的 requestId。");
        }
    }

    private void saveRecord(String userId, String requestId, String requestFingerprint, ChatResponse response) {
        ChatRequestRecordEntity record = new ChatRequestRecordEntity();
        record.setUserId(userId);
        record.setRequestId(requestId);
        record.setRequestFingerprint(requestFingerprint);
        record.setSessionId(response.getSessionId());
        record.setResponseJson(serializeResponse(response));
        repository.save(record);
    }

    private String serializeResponse(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cached chat response.", e);
        }
    }

    private ChatResponse deserializeResponse(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, ChatResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached chat response.", e);
        }
    }
}
