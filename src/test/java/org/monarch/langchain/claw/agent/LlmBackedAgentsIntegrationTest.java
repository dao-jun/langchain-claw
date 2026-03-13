package org.monarch.langchain.claw.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monarch.langchain.claw.LangchainClawApplication;
import org.monarch.langchain.claw.common.SessionState;
import org.monarch.langchain.claw.core.AgentOrchestrator;
import org.monarch.langchain.claw.config.ChatLanguageModelFactory;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.config.ModelSelection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = {LangchainClawApplication.class, LlmBackedAgentsIntegrationTest.LlmAgentTestConfig.class})
class LlmBackedAgentsIntegrationTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private ModelConfigService modelConfigService;

    @Autowired
    private StubChatLanguageModelFactory stubChatLanguageModelFactory;

    @BeforeEach
    void setUpModels() {
        stubChatLanguageModelFactory.reset();
        saveModel("llm-user", AgentType.PLAN, "stub-plan");
        saveModel("llm-user", AgentType.REVIEW, "stub-review");
        saveModel("llm-user", AgentType.EXECUTOR, "stub-executor");
        saveModel("llm-tool-user", AgentType.EXECUTOR, "stub-tool-executor");
        saveModel("llm-tool-fallback-user", AgentType.EXECUTOR, "stub-tool-executor-failure");
    }

    @Test
    void shouldUseLlmBackedPlanReviewAndConversationWhenConfigured() {
        AgentExecutionResult result = orchestrator.process("llm-user", null, "请帮我总结一下北京的情况");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan()).isNotNull();
        assertThat(result.getPlan().getSteps()).hasSize(1);
        assertThat(result.getPlan().getSteps().get(0).getExecutorType()).isEqualTo("conversation");
        assertThat(result.getMessage()).isEqualTo("这是来自 LLM conversation executor 的回复。");
        assertThat(stubChatLanguageModelFactory.requestedModels()).containsExactly("stub-plan", "stub-review", "stub-executor");
    }

    @Test
    void shouldExecuteCalculatorAndWeatherViaLlmToolCallingWhenConfigured() {
        AgentExecutionResult result = orchestrator.process("llm-tool-user", null, "请计算 8*(2+1)，并查询北京天气");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly(
            "8*(2+1) = 24",
            "北京 当前天气：晴，25°C（演示数据，可替换为真实天气 API）");
        assertThat(stubChatLanguageModelFactory.requestedModels()).contains("stub-tool-executor");
    }

    @Test
    void shouldFallbackToDirectToolExecutionWhenLlmToolCallingFails() {
        AgentExecutionResult result = orchestrator.process("llm-tool-fallback-user", null, "请计算 9*(2+1)");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("9*(2+1) = 27");
        assertThat(stubChatLanguageModelFactory.requestedModels()).contains("stub-tool-executor-failure");
    }

    private void saveModel(String userId, AgentType agentType, String modelName) {
        ModelSelection selection = new ModelSelection();
        selection.setAgentType(agentType);
        selection.setProvider("zhipu");
        selection.setModelName(modelName);
        selection.setTemperature(0.0d);
        selection.setMaxTokens(512);
        modelConfigService.save(userId, selection);
    }

    @TestConfiguration
    static class LlmAgentTestConfig {

        @Bean
        @Primary
        StubChatLanguageModelFactory stubChatLanguageModelFactory() {
            return new StubChatLanguageModelFactory();
        }
    }

    static class StubChatLanguageModelFactory implements ChatLanguageModelFactory {

        private final List<String> requestedModels = new ArrayList<>();

        @Override
        public ChatLanguageModel create(org.monarch.langchain.claw.config.ModelSelection selection) {
            requestedModels.add(selection.getModelName());
            return new ChatLanguageModel() {
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(responseFor(selection.getModelName(), messages));
                }

                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
                    return Response.from(responseFor(selection.getModelName(), messages));
                }
            };
        }

        void reset() {
            requestedModels.clear();
        }

        List<String> requestedModels() {
            return List.copyOf(requestedModels);
        }

        private AiMessage responseFor(String modelName, List<ChatMessage> messages) {
            if ("stub-plan".equals(modelName)) {
                return AiMessage.from("""
                    {
                      "rationale": "使用 LLM 生成会话步骤",
                      "steps": [
                        {
                          "stepId": "conversation-1",
                          "description": "LLM 会话回答",
                          "executorType": "conversation",
                          "requiredSkills": ["conversation"],
                          "dependsOn": [],
                          "parameters": {
                            "message": "请帮我总结一下北京的情况"
                          },
                          "inputBindings": {}
                        }
                      ]
                    }
                    """);
            }
            if ("stub-review".equals(modelName)) {
                return AiMessage.from("""
                    {
                      "decision": "ACCEPT",
                      "message": "审核通过"
                    }
                    """);
            }
            if ("stub-executor".equals(modelName)) {
                boolean hasConversationSystemPrompt = messages.stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .map(SystemMessage::text)
                    .anyMatch(text -> text.contains("conversation ExecutorAgent"));
                return AiMessage.from(hasConversationSystemPrompt
                    ? "这是来自 LLM conversation executor 的回复。"
                    : "unexpected");
            }
            if ("stub-tool-executor".equals(modelName)) {
                return toolCallingResponse(messages);
            }
            if ("stub-tool-executor-failure".equals(modelName)) {
                throw new IllegalStateException("Simulated tool-calling model failure");
            }
            throw new IllegalArgumentException("Unexpected model name: " + modelName);
        }

        private AiMessage toolCallingResponse(List<ChatMessage> messages) {
            ToolExecutionResultMessage toolExecutionResult = messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .findFirst()
                .orElse(null);
            if (toolExecutionResult != null) {
                return AiMessage.from(toolExecutionResult.text());
            }

            String prompt = messages.stream()
                .filter(dev.langchain4j.data.message.UserMessage.class::isInstance)
                .map(dev.langchain4j.data.message.UserMessage.class::cast)
                .map(dev.langchain4j.data.message.UserMessage::singleText)
                .findFirst()
                .orElse("");

            if (prompt.contains("executorType:\ncalculator")) {
                return AiMessage.from(List.of(
                    ToolExecutionRequest.builder()
                        .id("tool-call-calculator")
                        .name("calculator")
                        .arguments("{\"expression\":\"" + extractResolvedParameter(prompt, "expression", "0") + "\"}")
                        .build()));
            }
            if (prompt.contains("executorType:\nweather")) {
                return AiMessage.from(List.of(
                    ToolExecutionRequest.builder()
                        .id("tool-call-weather")
                        .name("weather")
                        .arguments("{\"city\":\"" + extractResolvedParameter(prompt, "city", "未知城市") + "\"}")
                        .build()));
            }
            return AiMessage.from("unexpected");
        }

        private String extractResolvedParameter(String prompt, String key, String defaultValue) {
            String marker = "\"" + key + "\":\"";
            int start = prompt.indexOf(marker);
            if (start < 0) {
                return defaultValue;
            }
            int valueStart = start + marker.length();
            int valueEnd = prompt.indexOf('"', valueStart);
            if (valueEnd < 0) {
                return defaultValue;
            }
            return prompt.substring(valueStart, valueEnd);
        }
    }
}
