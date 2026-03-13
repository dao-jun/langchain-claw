package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.common.SessionState;

public interface ExecutorAgent extends Agent {

    StepExecutionResult executeStep(AgentContext context, PlanStep step, StepExecutionContext stepContext);

    default List<String> executePlan(AgentContext context, org.monarch.langchain.claw.common.Plan plan) {
        List<String> outputs = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            StepExecutionResult result = executeStep(
                context,
                step,
                new StepExecutionContext(step.getParameters(), List.of(), List.of(), java.util.Map.of()));
            if (result.getOutputText() != null && !result.getOutputText().isBlank()) {
                outputs.add(result.getOutputText());
            }
        }
        return outputs;
    }

    boolean supports(String executorType);

    @Override
    default AgentType getType() {
        return AgentType.EXECUTOR;
    }

    @Override
    default AgentCapability getCapability() {
        AgentCapability capability = new AgentCapability();
        capability.setMaxRetries(1);
        capability.setTimeoutMs(10_000);
        capability.setSupportsParallel(true);
        return capability;
    }

    @Override
    default CompletableFuture<AgentExecutionResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> outputs = new ArrayList<>();
            for (PlanStep step : context.getPlan().getSteps()) {
                StepExecutionResult stepResult = executeStep(
                    context,
                    step,
                    new StepExecutionContext(step.getParameters(), List.of(), List.of(), java.util.Map.of()));
                if (stepResult.getOutputText() != null && !stepResult.getOutputText().isBlank()) {
                    outputs.add(stepResult.getOutputText());
                }
            }
            AgentExecutionResult result = new AgentExecutionResult();
            result.setSessionId(context.getSessionId());
            result.setPlan(context.getPlan());
            result.setRequestId(context.getRequestId());
            result.setTraceId(context.getTraceId());
            result.setStepOutputs(outputs);
            result.setMessage(String.join("\n", outputs));
            result.setNextState(SessionState.COMPLETED);
            return result;
        });
    }
}
