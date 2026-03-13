package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import java.util.List;
import org.monarch.langchain.claw.common.Plan;
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

    public ConversationExecutorAgent(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
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
    public List<String> executePlan(AgentContext context, Plan plan) {
        PlanStep step = plan.getSteps().get(0);
        HashMap<String, Object> input = new HashMap<>(step.getParameters());
        input.put("message", context.getUserMessage());
        input.put("memories", context.getLongTermMemories().stream().map(MemorySnippet::content).toList());
        input.put("prompt", resolvePrompt(context.getActiveSkills(), step));
        String output = toolRegistry.execute(step.getExecutorType(), input);
        step.setResult(output);
        return List.of(output);
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
