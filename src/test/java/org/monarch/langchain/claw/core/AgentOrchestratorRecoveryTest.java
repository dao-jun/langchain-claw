package org.monarch.langchain.claw.core;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monarch.langchain.claw.LangchainClawApplication;
import org.monarch.langchain.claw.agent.AgentContext;
import org.monarch.langchain.claw.agent.ExecutorAgent;
import org.monarch.langchain.claw.agent.PlanAgent;
import org.monarch.langchain.claw.agent.ReviewAgent;
import org.monarch.langchain.claw.agent.ReviewDecision;
import org.monarch.langchain.claw.agent.ReviewResult;
import org.monarch.langchain.claw.agent.StepExecutionContext;
import org.monarch.langchain.claw.agent.StepExecutionResult;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.common.PlanStepStatus;
import org.monarch.langchain.claw.common.SessionState;
import org.monarch.langchain.claw.session.SessionManager;
import org.monarch.langchain.claw.session.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

@SpringBootTest(classes = {LangchainClawApplication.class, AgentOrchestratorRecoveryTest.RecoveryTestConfig.class})
class AgentOrchestratorRecoveryTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecoveryExecutorAgent recoveryExecutorAgent;

    @BeforeEach
    void resetExecutorState() {
        recoveryExecutorAgent.reset();
    }

    @Test
    void shouldRetryFailedStepAndSucceedOnSecondAttempt() {
        var result = orchestrator.process("recovery-user-a", null, "retry-success");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("retry-success");
        assertThat(result.getPlan().getSteps().get(0).getRetryCount()).isEqualTo(1);
        assertThat(recoveryExecutorAgent.events()).containsExactly("primary#1", "primary#2");
    }

    @Test
    void shouldExecuteFallbackAndContinueDependentStep() {
        var result = orchestrator.process("recovery-user-b", null, "fallback-success");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("fallback-success", "source=fallback-value");
        PlanStep primary = result.getPlan().getSteps().get(0);
        assertThat(primary.getStatus()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(primary.getOutput()).containsEntry("value", "fallback-value");
        assertThat(primary.getFallbackStep()).isNotNull();
        assertThat(primary.getFallbackStep().getStatus()).isEqualTo(PlanStepStatus.COMPLETED);
    }

    @Test
    void shouldRunCompensationWhenExecutionStillFails() throws Exception {
        var result = orchestrator.process("recovery-user-c", "recovery-session-c", "compensation-failure");

        UserSession session = sessionManager.find("recovery-user-c", "recovery-session-c").orElseThrow();
        Plan persistedPlan = objectMapper.readValue(session.getCurrentPlanJson(), Plan.class);

        assertThat(result.getNextState()).isEqualTo(SessionState.FAILED);
        assertThat(result.getMessage()).contains("permanent failure");
        assertThat(session.getState()).isEqualTo(SessionState.FAILED);
        assertThat(recoveryExecutorAgent.events()).containsExactly("primary#1", "compensate#1");
        assertThat(persistedPlan.getSteps().get(0).getStatus()).isEqualTo(PlanStepStatus.FAILED);
        assertThat(persistedPlan.getSteps().get(0).getCompensationSteps()).hasSize(1);
        assertThat(persistedPlan.getSteps().get(0).getCompensationSteps().get(0).getStatus()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(persistedPlan.getSteps().get(0).getCompensationSteps().get(0).getResult()).isEqualTo("compensated");
    }

    @TestConfiguration
    static class RecoveryTestConfig {

        @Bean
        @Primary
        PlanAgent recoveryPlanAgent() {
            return new RecoveryPlanAgent();
        }

        @Bean
        @Primary
        ReviewAgent recoveryReviewAgent() {
            return new AcceptingReviewAgent();
        }

        @Bean
        RecoveryExecutorAgent recoveryExecutorAgent() {
            return new RecoveryExecutorAgent();
        }
    }

    static class RecoveryPlanAgent implements PlanAgent {

        @Override
        public String getName() {
            return "recoveryPlanAgent";
        }

        @Override
        public String getDescription() {
            return "Test-only plan agent for execution recovery scenarios.";
        }

        @Override
        public Plan generatePlan(AgentContext context) {
            return switch (context.getUserMessage()) {
                case "retry-success" -> retryPlan();
                case "fallback-success" -> fallbackPlan();
                case "compensation-failure" -> compensationPlan();
                default -> throw new IllegalArgumentException("Unexpected recovery test message: " + context.getUserMessage());
            };
        }

        private Plan retryPlan() {
            Plan plan = new Plan();
            plan.setRationale("retry");
            PlanStep step = new PlanStep();
            step.setStepId("primary");
            step.setExecutorType("recovery-test");
            step.setParameters(Map.of("mode", "fail-once-then-succeed", "value", "retry-value"));
            step.setMaxRetries(1);
            plan.setSteps(List.of(step));
            return plan;
        }

        private Plan fallbackPlan() {
            Plan plan = new Plan();
            plan.setRationale("fallback");
            PlanStep primary = new PlanStep();
            primary.setStepId("primary");
            primary.setExecutorType("recovery-test");
            primary.setParameters(Map.of("mode", "always-fail"));
            primary.setMaxRetries(0);

            PlanStep fallback = new PlanStep();
            fallback.setStepId("fallback");
            fallback.setExecutorType("recovery-test");
            fallback.setParameters(Map.of("mode", "succeed", "value", "fallback-value"));
            primary.setFallbackStep(fallback);

            PlanStep dependent = new PlanStep();
            dependent.setStepId("after-fallback");
            dependent.setExecutorType("recovery-test");
            dependent.setDependsOn(List.of("primary"));
            dependent.setInputBindings(Map.of("source", "${primary.value}"));
            dependent.setParameters(Map.of("mode", "use-source"));

            plan.setSteps(List.of(primary, dependent));
            return plan;
        }

        private Plan compensationPlan() {
            Plan plan = new Plan();
            plan.setRationale("compensation");
            PlanStep primary = new PlanStep();
            primary.setStepId("primary");
            primary.setExecutorType("recovery-test");
            primary.setParameters(Map.of("mode", "always-fail"));
            primary.setMaxRetries(0);

            PlanStep compensation = new PlanStep();
            compensation.setStepId("compensate");
            compensation.setExecutorType("recovery-test");
            compensation.setParameters(Map.of("mode", "compensate"));
            primary.setCompensationSteps(List.of(compensation));

            plan.setSteps(List.of(primary));
            return plan;
        }
    }

    static class AcceptingReviewAgent implements ReviewAgent {

        @Override
        public String getName() {
            return "acceptingReviewAgent";
        }

        @Override
        public String getDescription() {
            return "Test-only review agent that always accepts plans.";
        }

        @Override
        public ReviewResult review(AgentContext context) {
            return new ReviewResult(ReviewDecision.ACCEPT, "accepted");
        }
    }

    static class RecoveryExecutorAgent implements ExecutorAgent {

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        void reset() {
            attempts.clear();
            events.clear();
        }

        List<String> events() {
            return new ArrayList<>(events);
        }

        @Override
        public String getName() {
            return "recoveryExecutorAgent";
        }

        @Override
        public String getDescription() {
            return "Test-only executor that simulates retry/fallback/compensation behavior.";
        }

        @Override
        public StepExecutionResult executeStep(AgentContext context, PlanStep step, StepExecutionContext stepContext) {
            int attempt = attempts.computeIfAbsent(step.getStepId(), ignored -> new AtomicInteger()).incrementAndGet();
            events.add(step.getStepId() + "#" + attempt);
            String mode = String.valueOf(stepContext.getResolvedParameters().get("mode"));
            return switch (mode) {
                case "fail-once-then-succeed" -> failOnceThenSucceed(step, attempt, String.valueOf(stepContext.getResolvedParameters().get("value")));
                case "always-fail" -> throw new IllegalStateException("permanent failure");
                case "succeed" -> succeed(step, "fallback-success", String.valueOf(stepContext.getResolvedParameters().get("value")));
                case "use-source" -> useSource(step, String.valueOf(stepContext.getResolvedParameters().get("source")));
                case "compensate" -> compensate(step);
                default -> throw new IllegalArgumentException("Unknown recovery mode: " + mode);
            };
        }

        @Override
        public boolean supports(String executorType) {
            return "recovery-test".equalsIgnoreCase(executorType);
        }

        private StepExecutionResult failOnceThenSucceed(PlanStep step, int attempt, String value) {
            if (attempt == 1) {
                throw new IllegalStateException("transient failure");
            }
            return succeed(step, "retry-success", value);
        }

        private StepExecutionResult compensate(PlanStep step) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("compensated", true);
            step.setResult("compensated");
            step.setError(null);
            step.setOutput(output);
            return new StepExecutionResult("compensated", output);
        }

        private StepExecutionResult useSource(PlanStep step, String source) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("echo", source);
            step.setResult("source=" + source);
            step.setError(null);
            step.setOutput(output);
            return new StepExecutionResult("source=" + source, output);
        }

        private StepExecutionResult succeed(PlanStep step, String outputText, String value) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("value", value);
            step.setResult(outputText);
            step.setError(null);
            step.setOutput(output);
            return new StepExecutionResult(outputText, output);
        }
    }
}
