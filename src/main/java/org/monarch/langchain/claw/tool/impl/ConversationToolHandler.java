package org.monarch.langchain.claw.tool.impl;

import java.util.List;
import java.util.Map;
import org.monarch.langchain.claw.tool.ToolHandler;
import org.springframework.stereotype.Component;

@Component
public class ConversationToolHandler implements ToolHandler {

    @Override
    public String name() {
        return "conversation";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String message = String.valueOf(input.getOrDefault("message", ""));
        String prompt = String.valueOf(input.getOrDefault("prompt", ""));
        @SuppressWarnings("unchecked")
        List<String> memories = (List<String>) input.getOrDefault("memories", List.of());
        @SuppressWarnings("unchecked")
        List<String> priorStepOutputs = (List<String>) input.getOrDefault("priorStepOutputs", List.of());
        @SuppressWarnings("unchecked")
        List<String> dependencyStepOutputs = (List<String>) input.getOrDefault("dependencyStepOutputs", List.of());
        if (!priorStepOutputs.isEmpty()) {
            String prefix = "基于已完成步骤结果：" + String.join(" | ", priorStepOutputs);
            if (!prompt.isBlank()) {
                return prefix + "\n根据技能提示处理：" + prompt + "\n用户消息：" + message;
            }
            return prefix + "\n当前请求：" + message;
        }
        if (!dependencyStepOutputs.isEmpty()) {
            String prefix = "基于依赖步骤结果：" + String.join(" | ", dependencyStepOutputs);
            if (!prompt.isBlank()) {
                return prefix + "\n根据技能提示处理：" + prompt + "\n用户消息：" + message;
            }
            return prefix + "\n当前请求：" + message;
        }
        if (!prompt.isBlank()) {
            return "根据技能提示处理：" + prompt + "\n用户消息：" + message;
        }
        if (!memories.isEmpty()) {
            return "结合历史记忆回答：" + String.join(" | ", memories) + "\n当前请求：" + message;
        }
        return "已收到你的请求：" + message;
    }
}
