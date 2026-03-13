package org.monarch.langchain.claw.agent;

import org.monarch.langchain.claw.common.Plan;

public interface PlanAgent {
    Plan generatePlan(AgentContext context);
}
