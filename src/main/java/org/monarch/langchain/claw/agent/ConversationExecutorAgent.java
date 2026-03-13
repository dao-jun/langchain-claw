package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.monarch.langchain.claw.audit.ToolCallContext;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.memory.MemorySnippet;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class ConversationExecutorAgent implements ExecutorAgent {

    private final ToolRegistry toolRegistry;
    private final StepResultMapper stepResultMapper;

    public ConversationExecutorAgent(ToolRegistry toolRegistry, StepResultMapper stepResultMapper) {
        this.toolRegistry = toolRegistry;
        this.stepResultMapper = stepResultMapper;
    }

    @Override
    public String getName() {
        return "conversationExecutorAgent";
    }

    @Override
    public String getDescription() {
        return "负责执行 conversation 类型的步骤，并注入 prompt 与记忆信息。";
    }

    @Override
    public StepExecutionResult executeStep(AgentContext context, PlanStep step, StepExecutionContext stepContext) {
        HashMap<String, Object> input = new HashMap<>(stepContext.getResolvedParameters());
        input.putIfAbsent("message", context.getUserMessage());
        input.put("memories", context.getLongTermMemories().stream().map(MemorySnippet::content).toList());
        input.put("priorStepOutputs", stepContext.getPriorStepOutputs());
        input.put("dependencyStepOutputs", stepContext.getDependencyStepOutputs());
        input.put("dependencyOutputs", new LinkedHashMap<>(stepContext.getDependencyOutputs()));
        input.put("prompt", resolvePrompt(context.getActiveSkills(), step));
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
        return "conversation".equalsIgnoreCase(executorType);
    }

    private String resolvePrompt(List<SkillDefinition> activeSkills, PlanStep step) {
        String requiredSkill = step.getRequiredSkills().isEmpty() ? "" : step.getRequiredSkills().get(0);
        return activeSkills.stream()
            .filter(skill -> skill.getName().equalsIgnoreCase(requiredSkill))
            .map(SkillDefinition::getPrompt)
            .filter(prompt -> prompt != null && !prompt.isBlank())
            .findFirst()
            .orElse("");
    }
}
