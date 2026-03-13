package org.monarch.langchain.claw.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<ToolHandler> handlers) {
        handlers.forEach(handler -> tools.put(handler.name(), handler));
    }

    public String execute(String name, Map<String, Object> input) {
        ToolHandler handler = tools.get(name);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return handler.execute(input);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
