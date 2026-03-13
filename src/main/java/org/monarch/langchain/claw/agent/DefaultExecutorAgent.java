package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.memory.MemorySnippet;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class DefaultExecutorAgent implements ExecutorAgent {

    private final ToolRegistry toolRegistry;

    public DefaultExecutorAgent(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public List<String> execute(AgentContext context, Plan plan) {
        List<String> outputs = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            HashMap<String, Object> input = new HashMap<>(step.getParameters());
            if ("conversation".equals(step.getExecutorType())) {
                input.put("message", context.getUserMessage());
                input.put("memories", context.getLongTermMemories().stream().map(MemorySnippet::content).toList());
                input.put("prompt", resolvePrompt(context.getActiveSkills(), step));
            }
            String output = toolRegistry.execute(step.getExecutorType(), input);
            step.setResult(output);
            outputs.add(output);
        }
        return outputs;
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
