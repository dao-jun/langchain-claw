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
        assertThat(result.getStepOutputs()).contains("2+3*4 = 14");
        assertThat(result.getStepOutputs()).contains("北京 当前天气：晴，25°C（演示数据，可替换为真实天气 API）");
    }

    @Test
    void shouldAskUserWhenWeatherCityMissing() {
        AgentExecutionResult result = orchestrator.process("user-b", null, "帮我看看天气");

        assertThat(result.getNextState()).isEqualTo(SessionState.WAITING_FOR_USER);
        assertThat(result.getMessage()).contains("哪个城市");
    }

    @Test
    void shouldResumePendingWeatherPlanWhenUserProvidesCity() {
        AgentExecutionResult first = orchestrator.process("user-c", null, "帮我看看天气");
        AgentExecutionResult second = orchestrator.process("user-c", first.getSessionId(), "北京");

        assertThat(first.getNextState()).isEqualTo(SessionState.WAITING_FOR_USER);
        assertThat(second.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(second.getPlan().getSteps()).hasSize(1);
        assertThat(second.getPlan().getSteps().get(0).getExecutorType()).isEqualTo("weather");
        assertThat(second.getStepOutputs()).containsExactly("北京 当前天气：晴，25°C（演示数据，可替换为真实天气 API）");
    }

    @Test
    void shouldPlanMultipleWeatherStepsForMultipleCities() {
        AgentExecutionResult result = orchestrator.process("user-d", null, "请告诉我北京和上海的天气");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan().getSteps()).hasSize(2);
        assertThat(result.getPlan().getSteps()).extracting("executorType").containsExactly("weather", "weather");
        assertThat(result.getStepOutputs()).containsExactly(
            "北京 当前天气：晴，25°C（演示数据，可替换为真实天气 API）",
            "上海 当前天气：晴，25°C（演示数据，可替换为真实天气 API）");
    }

    @Test
    void shouldAppendConversationStepForMixedTaskAndExplanationRequest() {
        AgentExecutionResult result = orchestrator.process("user-e", null, "请计算 9*(2+1)，并解释一下结果");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan().getSteps()).hasSize(2);
        assertThat(result.getPlan().getSteps()).extracting("executorType").containsExactly("calculator", "conversation");
        assertThat(result.getStepOutputs().get(0)).isEqualTo("9*(2+1) = 27");
        assertThat(result.getStepOutputs().get(1)).contains("基于已完成步骤结果：9*(2+1) = 27");
    }

    @Test
    void shouldExecuteDependencyBoundAverageTemperatureWorkflow() {
        AgentExecutionResult result = orchestrator.process("user-f", null, "请查询北京和上海天气，并计算平均温度");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan().getSteps()).hasSize(3);
        assertThat(result.getPlan().getSteps()).extracting("stepId").containsExactly("weather-1", "weather-2", "calculator-1");
        assertThat(result.getPlan().getSteps().get(2).getDependsOn()).containsExactly("weather-1", "weather-2");
        assertThat(result.getPlan().getSteps().get(2).getInputBindings()).containsEntry("expression", "(${weather-1.temperatureC} + ${weather-2.temperatureC}) / 2");
        assertThat(result.getPlan().getSteps().get(0).getOutput()).containsEntry("temperatureC", 25.0d);
        assertThat(result.getPlan().getSteps().get(1).getOutput()).containsEntry("temperatureC", 25.0d);
        assertThat(result.getPlan().getSteps().get(2).getOutput()).containsEntry("numericValue", 25.0d);
        assertThat(result.getStepOutputs()).containsExactly(
            "北京 当前天气：晴，25°C（演示数据，可替换为真实天气 API）",
            "上海 当前天气：晴，25°C（演示数据，可替换为真实天气 API）",
            "(25.0 + 25.0) / 2 = 25");
    }

    @Test
    void shouldUseDependencyOutputsInConversationAfterAverageTemperatureWorkflow() {
        AgentExecutionResult result = orchestrator.process("user-g", null, "请查询北京和上海天气，计算平均温度，并总结一下");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getPlan().getSteps()).hasSize(4);
        assertThat(result.getPlan().getSteps()).extracting("executorType")
            .containsExactly("weather", "weather", "calculator", "conversation");
        assertThat(result.getPlan().getSteps().get(3).getDependsOn())
            .containsExactly("weather-1", "weather-2", "calculator-1");
        assertThat(result.getStepOutputs().get(3)).contains("基于已完成步骤结果：");
        assertThat(result.getStepOutputs().get(3)).contains("(25.0 + 25.0) / 2 = 25");
    }
}
