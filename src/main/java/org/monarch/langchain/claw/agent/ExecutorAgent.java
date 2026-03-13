package org.monarch.langchain.claw.agent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.SessionState;

public interface ExecutorAgent extends Agent {

    List<String> executePlan(AgentContext context, Plan plan);

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
            List<String> outputs = executePlan(context, context.getPlan());
            AgentExecutionResult result = new AgentExecutionResult();
            result.setSessionId(context.getSessionId());
            result.setPlan(context.getPlan());
            result.setStepOutputs(outputs);
            result.setMessage(String.join("\n", outputs));
            result.setNextState(SessionState.COMPLETED);
            return result;
        });
    }
}
