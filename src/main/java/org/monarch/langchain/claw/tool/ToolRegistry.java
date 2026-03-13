package org.monarch.langchain.claw.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.monarch.langchain.claw.audit.ToolCallAuditService;
import org.monarch.langchain.claw.audit.ToolCallContext;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> tools = new ConcurrentHashMap<>();
    private final ToolCallAuditService toolCallAuditService;

    public ToolRegistry(List<ToolHandler> handlers, ToolCallAuditService toolCallAuditService) {
        this.toolCallAuditService = toolCallAuditService;
        handlers.forEach(handler -> tools.put(handler.name(), handler));
    }

    public String execute(String name, Map<String, Object> input) {
        return execute(name, input, new ToolCallContext("system", "system", "system", "system"));
    }

    public String execute(String name, Map<String, Object> input, ToolCallContext context) {
        long startNanos = System.nanoTime();
        ToolHandler handler = tools.get(name);
        if (handler == null) {
            long durationMs = elapsedMs(startNanos);
            toolCallAuditService.recordFailure(context, name, input, "Unknown tool: " + name, durationMs);
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        try {
            String output = handler.execute(input);
            toolCallAuditService.recordSuccess(context, name, input, output, elapsedMs(startNanos));
            return output;
        } catch (RuntimeException ex) {
            toolCallAuditService.recordFailure(context, name, input, ex.getMessage(), elapsedMs(startNanos));
            throw ex;
        }
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    private long elapsedMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }
}
