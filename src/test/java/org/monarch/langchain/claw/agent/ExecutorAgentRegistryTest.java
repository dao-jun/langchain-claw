package org.monarch.langchain.claw.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExecutorAgentRegistryTest {

    @Autowired
    private ExecutorAgentRegistry registry;

    @Autowired
    private ConversationExecutorAgent conversationExecutorAgent;

    @Autowired
    private ToolDelegatingExecutorAgent toolDelegatingExecutorAgent;

    @Test
    void shouldRouteConversationStepsToConversationExecutor() {
        assertThat(registry.getExecutor("conversation")).isSameAs(conversationExecutorAgent);
    }

    @Test
    void shouldRouteToolStepsToDelegatingExecutor() {
        assertThat(registry.getExecutor("calculator")).isSameAs(toolDelegatingExecutorAgent);
        assertThat(registry.getExecutor("weather")).isSameAs(toolDelegatingExecutorAgent);
    }
}
