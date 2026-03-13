package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import org.monarch.langchain.claw.audit.ToolCallContext;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
public class ToolDelegatingExecutorAgent implements ExecutorAgent {

    private final ToolRegistry toolRegistry;
    private final StepResultMapper stepResultMapper;

    public ToolDelegatingExecutorAgent(ToolRegistry toolRegistry, StepResultMapper stepResultMapper) {
        this.toolRegistry = toolRegistry;
        this.stepResultMapper = stepResultMapper;
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
    public StepExecutionResult executeStep(AgentContext context, PlanStep step, StepExecutionContext stepContext) {
        HashMap<String, Object> input = new HashMap<>(stepContext.getResolvedParameters());
        String output = toolRegistry.execute(
            step.getExecutorType(),
            input,
            new ToolCallContext(context.getUserId(), context.getSessionId(), context.getRequestId(), context.getTraceId()));
        step.setParameters(input);
        step.setResult(output);
        step.setError(null);
        step.setOutput(stepResultMapper.map(step, input, output));
        return new StepExecutionResult(output, step.getOutput());
    }

    @Override
    public boolean supports(String executorType) {
        return toolRegistry.hasTool(executorType) && !"conversation".equalsIgnoreCase(executorType);
    }
}
