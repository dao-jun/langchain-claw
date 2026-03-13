package org.monarch.langchain.claw.core;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(classes = {LangchainClawApplication.class, AgentOrchestratorSchedulingTest.SchedulingTestConfig.class})
class AgentOrchestratorSchedulingTest {

    @Autowired
    private AgentOrchestrator orchestrator;

    @Autowired
    private SchedulingExecutorAgent schedulingExecutorAgent;

    @BeforeEach
    void resetExecutorState() {
        schedulingExecutorAgent.reset();
    }

    @Test
    void shouldSkipStepWhenConditionDoesNotMatch() {
        var result = orchestrator.process("scheduling-user-a", null, "condition-false");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("seed");
        assertThat(result.getPlan().getSteps()).hasSize(2);
        assertThat(result.getPlan().getSteps().get(0).getStatus()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(result.getPlan().getSteps().get(1).getStatus()).isEqualTo(PlanStepStatus.SKIPPED);
        assertThat(result.getPlan().getSteps().get(1).isConditionMatched()).isFalse();
    }

    @Test
    void shouldExecuteConditionalStepWhenConditionMatches() {
        var result = orchestrator.process("scheduling-user-b", null, "condition-true");

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("seed", "conditional");
        assertThat(result.getPlan().getSteps().get(1).getStatus()).isEqualTo(PlanStepStatus.COMPLETED);
        assertThat(result.getPlan().getSteps().get(1).isConditionMatched()).isTrue();
    }

    @Test
    void shouldExecuteIndependentReadyStepsInParallel() {
        var result = orchestrator.process("scheduling-user-c", null, "parallel-ready");
        List<String> events = schedulingExecutorAgent.events();

        assertThat(result.getNextState()).isEqualTo(SessionState.COMPLETED);
        assertThat(result.getStepOutputs()).containsExactly("left", "right", "join=left+right");
        assertThat(schedulingExecutorAgent.maxConcurrentExecutions()).isGreaterThanOrEqualTo(2);
        assertThat(events).containsExactlyInAnyOrder(
            "left-start",
            "right-start",
            "left-end",
            "right-end",
            "join-start",
            "join-end");
        assertThat(events.indexOf("join-start")).isGreaterThan(events.indexOf("left-end"));
        assertThat(events.indexOf("join-start")).isGreaterThan(events.indexOf("right-end"));
    }

    @TestConfiguration
    static class SchedulingTestConfig {

        @Bean
        @Primary
        PlanAgent schedulingPlanAgent() {
            return new SchedulingPlanAgent();
        }

        @Bean
        @Primary
        ReviewAgent schedulingReviewAgent() {
            return new SchedulingReviewAgent();
        }

        @Bean
        SchedulingExecutorAgent schedulingExecutorAgent() {
            return new SchedulingExecutorAgent();
        }
    }

    static class SchedulingPlanAgent implements PlanAgent {

        @Override
        public String getName() {
            return "schedulingPlanAgent";
        }

        @Override
        public String getDescription() {
            return "Test-only plan agent for condition and parallel scheduling scenarios.";
        }

        @Override
        public Plan generatePlan(AgentContext context) {
            return switch (context.getUserMessage()) {
                case "condition-false" -> conditionalPlan("skip-me", "${seed.value} == \"run\"");
                case "condition-true" -> conditionalPlan("conditional", "${seed.value} == \"run\"");
                case "parallel-ready" -> parallelPlan();
                default -> throw new IllegalArgumentException("Unexpected scheduling test message: " + context.getUserMessage());
            };
        }

        private Plan conditionalPlan(String conditionalValue, String condition) {
            Plan plan = new Plan();
            plan.setRationale("conditional");

            PlanStep seed = new PlanStep();
            seed.setStepId("seed");
            seed.setExecutorType("scheduling-test");
            seed.setParameters(Map.of("mode", "seed", "value", conditionalValue.equals("conditional") ? "run" : "skip"));

            PlanStep conditional = new PlanStep();
            conditional.setStepId("conditional");
            conditional.setExecutorType("scheduling-test");
            conditional.setDependsOn(List.of("seed"));
            conditional.setCondition(condition);
            conditional.setParameters(Map.of("mode", "emit", "value", conditionalValue));

            plan.setSteps(List.of(seed, conditional));
            return plan;
        }

        private Plan parallelPlan() {
            Plan plan = new Plan();
            plan.setRationale("parallel");

            PlanStep left = new PlanStep();
            left.setStepId("left");
            left.setExecutorType("scheduling-test");
            left.setParameters(Map.of("mode", "delayed-emit", "value", "left", "delayMs", 150));

            PlanStep right = new PlanStep();
            right.setStepId("right");
            right.setExecutorType("scheduling-test");
            right.setParameters(Map.of("mode", "delayed-emit", "value", "right", "delayMs", 150));

            PlanStep join = new PlanStep();
            join.setStepId("join");
            join.setExecutorType("scheduling-test");
            join.setDependsOn(List.of("left", "right"));
            join.setInputBindings(Map.of("leftValue", "${left.value}", "rightValue", "${right.value}"));
            join.setParameters(Map.of("mode", "join"));

            plan.setSteps(List.of(left, right, join));
            return plan;
        }
    }

    static class SchedulingReviewAgent implements ReviewAgent {

        @Override
        public String getName() {
            return "schedulingReviewAgent";
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

    static class SchedulingExecutorAgent implements ExecutorAgent {

        private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();
        private final Map<String, AtomicInteger> executions = new ConcurrentHashMap<>();
        private final AtomicInteger activeExecutions = new AtomicInteger();
        private final AtomicInteger maxConcurrentExecutions = new AtomicInteger();

        void reset() {
            events.clear();
            executions.clear();
            activeExecutions.set(0);
            maxConcurrentExecutions.set(0);
        }

        List<String> events() {
            return new ArrayList<>(events);
        }

        int maxConcurrentExecutions() {
            return maxConcurrentExecutions.get();
        }

        @Override
        public String getName() {
            return "schedulingExecutorAgent";
        }

        @Override
        public String getDescription() {
            return "Test-only executor that records conditional and parallel scheduling behavior.";
        }

        @Override
        public StepExecutionResult executeStep(AgentContext context, PlanStep step, StepExecutionContext stepContext) {
            executions.computeIfAbsent(step.getStepId(), ignored -> new AtomicInteger()).incrementAndGet();
            events.add(step.getStepId() + "-start");
            int active = activeExecutions.incrementAndGet();
            maxConcurrentExecutions.accumulateAndGet(active, Math::max);
            try {
                String mode = String.valueOf(stepContext.getResolvedParameters().get("mode"));
                return switch (mode) {
                    case "seed" -> emit(step, "seed", String.valueOf(stepContext.getResolvedParameters().get("value")));
                    case "emit" -> emit(step, String.valueOf(stepContext.getResolvedParameters().get("value")),
                        String.valueOf(stepContext.getResolvedParameters().get("value")));
                    case "delayed-emit" -> delayedEmit(
                        step,
                        String.valueOf(stepContext.getResolvedParameters().get("value")),
                        ((Number) stepContext.getResolvedParameters().get("delayMs")).longValue());
                    case "join" -> join(step, stepContext);
                    default -> throw new IllegalArgumentException("Unknown scheduling mode: " + mode);
                };
            } finally {
                activeExecutions.decrementAndGet();
                events.add(step.getStepId() + "-end");
            }
        }

        @Override
        public boolean supports(String executorType) {
            return "scheduling-test".equalsIgnoreCase(executorType);
        }

        private StepExecutionResult delayedEmit(PlanStep step, String value, long delayMs) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during scheduling test.", e);
            }
            return emit(step, value, value);
        }

        private StepExecutionResult join(PlanStep step, StepExecutionContext stepContext) {
            String left = String.valueOf(stepContext.getResolvedParameters().get("leftValue"));
            String right = String.valueOf(stepContext.getResolvedParameters().get("rightValue"));
            return emit(step, "join=" + left + "+" + right, left + "+" + right);
        }

        private StepExecutionResult emit(PlanStep step, String outputText, String value) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("value", value);
            step.setResult(outputText);
            step.setError(null);
            step.setOutput(output);
            return new StepExecutionResult(outputText, output);
        }
    }
}
