package org.monarch.langchain.claw.agent;

import java.util.concurrent.CompletableFuture;

public interface Agent {

    String getName();

    AgentType getType();

    String getDescription();

    AgentCapability getCapability();

    CompletableFuture<AgentExecutionResult> execute(AgentContext context);
}
