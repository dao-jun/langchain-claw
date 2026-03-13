package org.monarch.langchain.claw.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.monarch.langchain.claw.audit.ToolCallContext;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.tool.ToolCallContextHolder;
import org.monarch.langchain.claw.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(100)
public class ToolDelegatingExecutorAgent implements ExecutorAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolDelegatingExecutorAgent.class);
    private final ToolRegistry toolRegistry;
    private final StepResultMapper stepResultMapper;
    private final LangChain4jAgentServiceFactory serviceFactory;
    private final LlmJsonSupport llmJsonSupport;
    private final ToolCallContextHolder toolCallContextHolder;

    public ToolDelegatingExecutorAgent(ToolRegistry toolRegistry,
                                       StepResultMapper stepResultMapper,
                                       LangChain4jAgentServiceFactory serviceFactory,
                                       LlmJsonSupport llmJsonSupport,
                                       ToolCallContextHolder toolCallContextHolder) {
        this.toolRegistry = toolRegistry;
        this.stepResultMapper = stepResultMapper;
        this.serviceFactory = serviceFactory;
        this.llmJsonSupport = llmJsonSupport;
        this.toolCallContextHolder = toolCallContextHolder;
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
        ToolCallContext toolCallContext = new ToolCallContext(context.getUserId(), context.getSessionId(), context.getRequestId(), context.getTraceId());
        String output = shouldUseLlm(context, step)
            ? executeWithLlm(context, step, stepContext, input, toolCallContext)
            : toolRegistry.execute(step.getExecutorType(), input, toolCallContext);
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

    private String executeWithLlm(AgentContext context,
                                  PlanStep step,
                                  StepExecutionContext stepContext,
                                  HashMap<String, Object> input,
                                  ToolCallContext toolCallContext) {
        try (ToolCallContextHolder.Scope ignored = toolCallContextHolder.withContext(toolCallContext)) {
            return serviceFactory.toolCallingExecutorService(context.getModelSelection()).execute(
                step.getExecutorType(),
                resolvePrompt(context.getActiveSkills(), step),
                context.getUserMessage(),
                llmJsonSupport.toJson(input),
                llmJsonSupport.toJson(stepContext.getPriorStepOutputs()),
                llmJsonSupport.toJson(stepContext.getDependencyOutputs()));
        } catch (RuntimeException ex) {
            log.warn("Falling back to direct tool execution. provider={}, model={}, executorType={}, error={}",
                context.getModelSelection().getProvider(),
                context.getModelSelection().getModelName(),
                step.getExecutorType(),
                ex.getMessage());
            return toolRegistry.execute(step.getExecutorType(), input, toolCallContext);
        }
    }

    private boolean shouldUseLlm(AgentContext context, PlanStep step) {
        String provider = context.getModelSelection() == null ? null : context.getModelSelection().getProvider();
        return StringUtils.hasText(provider)
            && !"rule-based".equals(provider.trim().toLowerCase(Locale.ROOT))
            && List.of("calculator", "weather").contains(step.getExecutorType());
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
