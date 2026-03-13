package org.monarch.langchain.claw.audit;

public record ToolCallContext(
    String userId,
    String sessionId,
    String requestId,
    String traceId
) {
}
