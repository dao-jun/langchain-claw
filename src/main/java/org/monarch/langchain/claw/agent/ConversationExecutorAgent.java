package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import org.monarch.langchain.claw.audit.ToolCallContext;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.memory.MemorySnippet;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(0)
public class ConversationExecutorAgent implements ExecutorAgent {

    private static final Logger log = LoggerFactory.getLogger(ConversationExecutorAgent.class);
    private final ToolRegistry toolRegistry;
    private final StepResultMapper stepResultMapper;
    private final LangChain4jAgentServiceFactory serviceFactory;
    private final LlmJsonSupport llmJsonSupport;

    public ConversationExecutorAgent(ToolRegistry toolRegistry,
                                     StepResultMapper stepResultMapper,
                                     LangChain4jAgentServiceFactory serviceFactory,
                                     LlmJsonSupport llmJsonSupport) {
        this.toolRegistry = toolRegistry;
        this.stepResultMapper = stepResultMapper;
        this.serviceFactory = serviceFactory;
        this.llmJsonSupport = llmJsonSupport;
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
        String output = shouldUseLlm(context)
            ? executeWithLlm(context, input)
            : toolRegistry.execute(
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

    private String executeWithLlm(AgentContext context, HashMap<String, Object> input) {
        try {
            return serviceFactory.conversationService(context.getModelSelection()).respond(
                String.valueOf(input.getOrDefault("prompt", "")),
                String.valueOf(input.getOrDefault("message", context.getUserMessage())),
                llmJsonSupport.toJson(input),
                llmJsonSupport.toJson(context.getShortTermMessages()),
                llmJsonSupport.toJson(context.getLongTermMemories().stream().map(MemorySnippet::content).toList()),
                llmJsonSupport.toJson(input.getOrDefault("priorStepOutputs", List.of())),
                llmJsonSupport.toJson(input.getOrDefault("dependencyStepOutputs", List.of())),
                llmJsonSupport.toJson(input.getOrDefault("dependencyOutputs", java.util.Map.of())));
        } catch (RuntimeException ex) {
            log.warn("Falling back to rule-based conversation tool. provider={}, model={}, error={}",
                context.getModelSelection().getProvider(),
                context.getModelSelection().getModelName(),
                ex.getMessage());
            return toolRegistry.execute(
                "conversation",
                input,
                new ToolCallContext(context.getUserId(), context.getSessionId(), context.getRequestId(), context.getTraceId()));
        }
    }

    private boolean shouldUseLlm(AgentContext context) {
        String provider = context.getModelSelection() == null ? null : context.getModelSelection().getProvider();
        return StringUtils.hasText(provider) && !"rule-based".equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
