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
        if (!prompt.isBlank()) {
            return "根据技能提示处理：" + prompt + "\n用户消息：" + message;
        }
        if (!memories.isEmpty()) {
            return "结合历史记忆回答：" + String.join(" | ", memories) + "\n当前请求：" + message;
        }
        return "已收到你的请求：" + message;
    }
}
