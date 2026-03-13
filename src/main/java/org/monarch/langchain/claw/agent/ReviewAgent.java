package org.monarch.langchain.claw.agent;

import java.util.concurrent.CompletableFuture;
import org.monarch.langchain.claw.common.SessionState;

public interface ReviewAgent extends Agent {

    ReviewResult review(AgentContext context);

    @Override
    default AgentType getType() {
        return AgentType.REVIEW;
    }

    @Override
    default AgentCapability getCapability() {
        AgentCapability capability = new AgentCapability();
        capability.setMaxRetries(1);
        capability.setTimeoutMs(3_000);
        capability.setSupportsParallel(false);
        return capability;
    }

    @Override
    default CompletableFuture<AgentExecutionResult> execute(AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            ReviewResult review = review(context);
            AgentExecutionResult result = new AgentExecutionResult();
            result.setSessionId(context.getSessionId());
            result.setPlan(context.getPlan());
            result.setMessage(review.message());
            result.setNextState(switch (review.decision()) {
                case ACCEPT -> SessionState.PLAN_REVIEWED;
                case REJECT -> SessionState.FAILED;
                case ASK_USER -> SessionState.WAITING_FOR_USER;
            });
            return result;
        });
    }
}
