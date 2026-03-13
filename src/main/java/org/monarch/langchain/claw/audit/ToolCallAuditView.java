package org.monarch.langchain.claw.audit;

import java.time.Instant;

public record ToolCallAuditView(
    String requestId,
    String traceId,
    String toolName,
    boolean success,
    long durationMs,
    String toolInput,
    String toolOutput,
    String errorMessage,
    Instant createdAt
) {
}
