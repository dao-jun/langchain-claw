package org.monarch.langchain.claw.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monarch.langchain.claw.LangchainClawApplication;
import org.monarch.langchain.claw.core.AgentOrchestrator;
import org.monarch.langchain.claw.config.ChatLanguageModelFactory;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.config.ModelSelection;
import org.monarch.langchain.claw.common.SessionState;
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
            return messages -> Response.from(AiMessage.from(responseFor(selection.getModelName(), messages)));
        }

        void reset() {
            requestedModels.clear();
        }

        List<String> requestedModels() {
            return List.copyOf(requestedModels);
        }

        private String responseFor(String modelName, List<ChatMessage> messages) {
            if ("stub-plan".equals(modelName)) {
                return """
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
                    """;
            }
            if ("stub-review".equals(modelName)) {
                return """
                    {
                      "decision": "ACCEPT",
                      "message": "审核通过"
                    }
                    """;
            }
            if ("stub-executor".equals(modelName)) {
                boolean hasConversationSystemPrompt = messages.stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .map(SystemMessage::text)
                    .anyMatch(text -> text.contains("conversation ExecutorAgent"));
                return hasConversationSystemPrompt
                    ? "这是来自 LLM conversation executor 的回复。"
                    : "unexpected";
            }
            throw new IllegalArgumentException("Unexpected model name: " + modelName);
        }
    }
}
