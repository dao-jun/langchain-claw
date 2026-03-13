package org.monarch.langchain.claw.agent;

import java.util.List;
import org.monarch.langchain.claw.common.Plan;

public interface ExecutorAgent {
    List<String> execute(AgentContext context, Plan plan);
}
