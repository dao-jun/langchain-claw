package org.monarch.langchain.claw.tools;

import dev.langchain4j.agent.tool.Tool;

public class CalculatorTool {
    @Tool("计算数学表达式，例如 '2+2' 或 'sqrt(16)'")
    public double calculate(String expression) {
        // 简单实现，实际可使用 ScriptEngine
        try {
            // 仅作示例，生产环境应使用安全计算
            return new javax.script.ScriptEngineManager()
                    .getEngineByName("JavaScript")
                    .eval(expression) instanceof Number n ? n.doubleValue() : 0;
        } catch (Exception e) {
            throw new RuntimeException("计算失败: " + e.getMessage());
        }
    }
}
