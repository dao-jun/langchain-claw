package org.monarch.langchain.claw.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.monarch.langchain.claw.agent.AgentExecutionResult;
import org.monarch.langchain.claw.common.SessionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentOrchestratorTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Test
    void shouldExecuteMultiStepPlanWithBuiltinSkills() {
        AgentExecutionResult result = orchestrator.process("user-a", null, "请帮我计算 2+3*4 ，并告诉我北京天气");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan()).isNotNull();
        assertThat(result.getPlan().getSteps()).hasSize(2);
        assertThat(result.getStepOutputs()).anyMatch(output -> output.contains("14"));
        assertThat(result.getStepOutputs()).anyMatch(output -> output.contains("北京"));
    }

    @Test
    void shouldAskUserWhenWeatherCityMissing() {
        AgentExecutionResult result = orchestrator.process("user-b", null, "帮我看看天气");

        assertThat(result.getNextState()).isEqualTo(SessionState.WAITING_FOR_USER);
        assertThat(result.getMessage()).contains("哪个城市");
    }
}
