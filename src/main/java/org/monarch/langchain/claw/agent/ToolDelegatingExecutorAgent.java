package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import java.util.List;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class ToolDelegatingExecutorAgent implements ExecutorAgent {

    private final ToolRegistry toolRegistry;

    public ToolDelegatingExecutorAgent(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String getName() {
        return "toolDelegatingExecutorAgent";
    }

    @Override
    public String getDescription() {
        return "默认执行器，按 executorType 将步骤转发给已注册工具。";
    }

    @Override
    public List<String> executePlan(AgentContext context, Plan plan) {
        PlanStep step = plan.getSteps().get(0);
        String output = toolRegistry.execute(step.getExecutorType(), new HashMap<>(step.getParameters()));
        step.setResult(output);
        return List.of(output);
    }

    @Override
    public boolean supports(String executorType) {
        return toolRegistry.hasTool(executorType) && !"conversation".equalsIgnoreCase(executorType);
    }
}
