package org.monarch.langchain.claw.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LangChain4jExecutionTools {

    private final ToolRegistry toolRegistry;
    private final ToolCallContextHolder toolCallContextHolder;

    public LangChain4jExecutionTools(ToolRegistry toolRegistry, ToolCallContextHolder toolCallContextHolder) {
        this.toolRegistry = toolRegistry;
        this.toolCallContextHolder = toolCallContextHolder;
    }

    @Tool("计算数学表达式并返回工具原始结果。")
    public String calculator(@P("expression") String expression) {
        return toolRegistry.execute(
            "calculator",
            Map.of("expression", expression),
            toolCallContextHolder.currentOrDefault());
    }

    @Tool("查询城市天气并返回工具原始结果。")
    public String weather(@P("city") String city) {
        return toolRegistry.execute(
            "weather",
            Map.of("city", city),
            toolCallContextHolder.currentOrDefault());
    }
}
