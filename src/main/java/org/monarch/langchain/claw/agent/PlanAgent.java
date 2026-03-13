package org.monarch.langchain.claw.agent;

import java.util.concurrent.CompletableFuture;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.SessionState;

public interface PlanAgent extends Agent {

    Plan generatePlan(AgentContext context);

    @Override
    default AgentType getType() {
        return AgentType.PLAN;
    }

    @Override
    default AgentCapability getCapability() {
        AgentCapability capability = new AgentCapability();
        capability.setMaxRetries(1);
        capability.setTimeoutMs(5_000);
        capability.setSupportsParallel(false);
        return capability;
    }

    @Override
    default CompletableFuture<AgentExecutionResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            Plan plan = generatePlan(context);
            AgentExecutionResult result = new AgentExecutionResult();
            result.setSessionId(context.getSessionId());
            result.setPlan(plan);
            result.setRequestId(context.getRequestId());
            result.setTraceId(context.getTraceId());
            result.setNextState(SessionState.PLAN_GENERATED);
            result.setMessage("计划已生成");
            return result;
        });
    }
}
