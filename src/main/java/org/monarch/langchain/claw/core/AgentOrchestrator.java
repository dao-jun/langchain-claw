package org.monarch.langchain.claw.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;
import org.monarch.langchain.claw.agent.AgentContext;
import org.monarch.langchain.claw.agent.AgentExecutionResult;
import org.monarch.langchain.claw.agent.AgentType;
import org.monarch.langchain.claw.agent.ExecutorAgentRegistry;
import org.monarch.langchain.claw.agent.PlanAgent;
import org.monarch.langchain.claw.agent.ReviewAgent;
import org.monarch.langchain.claw.agent.StepExecutionContext;
import org.monarch.langchain.claw.agent.StepExecutionResult;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.common.PlanStepStatus;
import org.monarch.langchain.claw.common.SessionState;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.memory.MemoryManager;
import org.monarch.langchain.claw.memory.MemorySnippet;
import org.monarch.langchain.claw.session.SessionManager;
import org.monarch.langchain.claw.session.UserSession;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.skill.SkillManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AgentOrchestrator {

    private static final Pattern BINDING_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final SessionManager sessionManager;
    private final SkillManager skillManager;
    private final MemoryManager memoryManager;
    private final ModelConfigService modelConfigService;
    private final PlanAgent planAgent;
    private final ReviewAgent reviewAgent;
    private final ExecutorAgentRegistry executorAgentRegistry;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(SessionManager sessionManager,
                             SkillManager skillManager,
                             MemoryManager memoryManager,
                             ModelConfigService modelConfigService,
                             PlanAgent planAgent,
                             ReviewAgent reviewAgent,
                             ExecutorAgentRegistry executorAgentRegistry,
                             ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.skillManager = skillManager;
        this.memoryManager = memoryManager;
        this.modelConfigService = modelConfigService;
        this.planAgent = planAgent;
        this.reviewAgent = reviewAgent;
        this.executorAgentRegistry = executorAgentRegistry;
        this.objectMapper = objectMapper;
    }

    public AgentExecutionResult process(String userId, String maybeSessionId, String message) {
        return process(userId, maybeSessionId, message, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public AgentExecutionResult process(String userId, String maybeSessionId, String message, String requestId, String traceId) {
        UserSession session = sessionManager.getOrCreate(userId, maybeSessionId);
        sessionManager.appendMessage(session.getSessionId(), "user", message);
        List<SkillDefinition> skills = skillManager.getAvailableSkills(userId);
        List<String> shortTermMessages = sessionManager.recentMessages(session.getSessionId(), 20);
        List<MemorySnippet> memories = memoryManager.recall(userId, message, 5);
        Plan pendingPlan = session.getState() == SessionState.WAITING_FOR_USER ? deserializePlan(session.getCurrentPlanJson()) : null;

        AgentContext planningContext = new AgentContext(
            userId,
            session.getSessionId(),
            message,
            session,
            skills,
            shortTermMessages,
            memories,
            modelConfigService.resolve(userId, AgentType.PLAN),
            pendingPlan,
            requestId,
            traceId);
        AgentExecutionResult planResult = planAgent.execute(planningContext).join();
        Plan plan = planResult.getPlan();
        normalizePlan(plan);
        sessionManager.updateState(userId, session.getSessionId(), SessionState.PLAN_GENERATED, null, serialize(plan));

        AgentContext reviewContext = new AgentContext(
            userId,
            session.getSessionId(),
            message,
            session,
            skills,
            shortTermMessages,
            memories,
            modelConfigService.resolve(userId, AgentType.REVIEW),
            plan,
            requestId,
            traceId);
        AgentExecutionResult review = reviewAgent.execute(reviewContext).join();
        if (review.getNextState() == SessionState.PLAN_REVIEWED) {
            sessionManager.updateState(userId, session.getSessionId(), SessionState.PLAN_REVIEWED, null, serialize(plan));
        }

        AgentExecutionResult result = new AgentExecutionResult();
        result.setSessionId(session.getSessionId());
        result.setPlan(plan);
        result.setRequestId(requestId);
        result.setTraceId(traceId);
        if (review.getNextState() == SessionState.WAITING_FOR_USER) {
            sessionManager.updateState(userId, session.getSessionId(), SessionState.WAITING_FOR_USER, review.getMessage(), serialize(plan));
            result.setNextState(SessionState.WAITING_FOR_USER);
            result.setMessage(review.getMessage());
            sessionManager.appendMessage(session.getSessionId(), "assistant", review.getMessage());
            return result;
        }
        if (review.getNextState() == SessionState.FAILED) {
            sessionManager.updateState(userId, session.getSessionId(), SessionState.FAILED, review.getMessage(), serialize(plan));
            result.setNextState(SessionState.FAILED);
            result.setMessage(review.getMessage());
            sessionManager.appendMessage(session.getSessionId(), "assistant", review.getMessage());
            return result;
        }

        sessionManager.updateState(userId, session.getSessionId(), SessionState.EXECUTING, null, serialize(plan));
        List<String> outputs;
        try {
            outputs = executePlan(userId, session, message, skills, shortTermMessages, memories, plan, requestId, traceId);
        } catch (RuntimeException ex) {
            sessionManager.updateState(userId, session.getSessionId(), SessionState.FAILED, ex.getMessage(), serialize(plan));
            throw ex;
        }
        String response = String.join("\n", outputs);
        sessionManager.updateState(userId, session.getSessionId(), SessionState.COMPLETED, null, serialize(plan));
        sessionManager.appendMessage(session.getSessionId(), "assistant", response);
        memoryManager.storeConversation(userId, session.getSessionId(), "用户: " + message + "\n助手: " + response);
        result.setNextState(SessionState.COMPLETED);
        result.setMessage(response);
        result.setStepOutputs(outputs);
        return result;
    }

    private List<String> executePlan(String userId,
                                     UserSession session,
                                     String message,
                                     List<SkillDefinition> skills,
                                     List<String> shortTermMessages,
                                     List<MemorySnippet> memories,
                                     Plan plan,
                                     String requestId,
                                     String traceId) {
        List<String> outputs = new ArrayList<>();
        List<PlanStep> pendingSteps = new ArrayList<>(plan.getSteps());
        Map<String, StepExecutionResult> completedSteps = new LinkedHashMap<>();

        while (!pendingSteps.isEmpty()) {
            PlanStep nextStep = findNextReadyStep(pendingSteps, completedSteps);
            pendingSteps.remove(nextStep);
            StepExecutionContext stepContext = createStepContext(nextStep, completedSteps, outputs);
            try {
                StepExecutionResult executionResult = executeStep(
                    userId,
                    session,
                    message,
                    skills,
                    shortTermMessages,
                    memories,
                    plan,
                    nextStep,
                    stepContext,
                    requestId,
                    traceId);
                completedSteps.put(nextStep.getStepId(), executionResult);
                if (executionResult.getOutputText() != null && !executionResult.getOutputText().isBlank()) {
                    outputs.add(executionResult.getOutputText());
                }
            } catch (RuntimeException ex) {
                nextStep.setStatus(PlanStepStatus.FAILED);
                nextStep.setError(ex.getMessage());
                throw ex;
            }
        }
        return outputs;
    }

    private StepExecutionResult executeStep(String userId,
                                            UserSession session,
                                            String message,
                                            List<SkillDefinition> skills,
                                            List<String> shortTermMessages,
                                            List<MemorySnippet> memories,
                                            Plan plan,
                                            PlanStep step,
                                            StepExecutionContext stepContext,
                                            String requestId,
                                            String traceId) {
        Plan singleStepPlan = new Plan();
        singleStepPlan.setRationale(plan.getRationale());
        PlanStep executionStep = copyStep(step);
        executionStep.setParameters(new HashMap<>(stepContext.getResolvedParameters()));
        singleStepPlan.setSteps(List.of(executionStep));
        AgentContext executionContext = new AgentContext(
            userId,
            session.getSessionId(),
            message,
            session,
            skills,
            shortTermMessages,
            memories,
            modelConfigService.resolve(userId, AgentType.EXECUTOR),
            singleStepPlan,
            requestId,
            traceId);
        StepExecutionResult executionResult = executorAgentRegistry
            .getExecutor(step.getExecutorType())
            .executeStep(executionContext, executionStep, stepContext);
        step.setParameters(executionStep.getParameters());
        step.setResult(executionStep.getResult());
        step.setError(executionStep.getError());
        step.setOutput(new LinkedHashMap<>(executionResult.getOutputData()));
        step.setStatus(PlanStepStatus.COMPLETED);
        return executionResult;
    }

    private PlanStep copyStep(PlanStep source) {
        PlanStep copy = new PlanStep();
        copy.setStepId(source.getStepId());
        copy.setDescription(source.getDescription());
        copy.setExecutorType(source.getExecutorType());
        copy.setRequiredSkills(new ArrayList<>(source.getRequiredSkills()));
        copy.setDependsOn(new ArrayList<>(source.getDependsOn()));
        copy.setParameters(new HashMap<>(source.getParameters()));
        copy.setInputBindings(new HashMap<>(source.getInputBindings()));
        copy.setOutput(new HashMap<>(source.getOutput()));
        copy.setStatus(source.getStatus());
        copy.setResult(source.getResult());
        copy.setError(source.getError());
        return copy;
    }

    private void normalizePlan(Plan plan) {
        Map<String, Integer> counters = new HashMap<>();
        for (PlanStep step : plan.getSteps()) {
            if (step.getStepId() == null || step.getStepId().isBlank()) {
                int index = counters.merge(step.getExecutorType(), 1, Integer::sum);
                step.setStepId(step.getExecutorType() + "-" + index);
            }
            if (step.getDependsOn() == null) {
                step.setDependsOn(new ArrayList<>());
            }
            if (step.getParameters() == null) {
                step.setParameters(new HashMap<>());
            }
            if (step.getInputBindings() == null) {
                step.setInputBindings(new HashMap<>());
            }
            if (step.getOutput() == null) {
                step.setOutput(new HashMap<>());
            }
            if (step.getStatus() == null) {
                step.setStatus(PlanStepStatus.PENDING);
            }
        }
    }

    private PlanStep findNextReadyStep(List<PlanStep> pendingSteps, Map<String, StepExecutionResult> completedSteps) {
        return pendingSteps.stream()
            .filter(step -> completedSteps.keySet().containsAll(step.getDependsOn()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(buildDependencyErrorMessage(pendingSteps, completedSteps)));
    }

    private String buildDependencyErrorMessage(List<PlanStep> pendingSteps, Map<String, StepExecutionResult> completedSteps) {
        List<String> completedStepIds = new ArrayList<>(completedSteps.keySet());
        List<String> unresolvedDetails = pendingSteps.stream()
            .map(step -> {
                List<String> missingDependencies = step.getDependsOn().stream()
                    .filter(dependencyId -> !completedSteps.containsKey(dependencyId))
                    .toList();
                return step.getStepId() + " missing " + missingDependencies;
            })
            .toList();
        return "Plan contains unresolved or cyclic step dependencies. completed="
            + completedStepIds
            + ", pending="
            + unresolvedDetails;
    }

    private StepExecutionContext createStepContext(PlanStep step,
                                                   Map<String, StepExecutionResult> completedSteps,
                                                   List<String> priorOutputs) {
        Map<String, Object> resolvedParameters = new LinkedHashMap<>(step.getParameters());
        for (Map.Entry<String, String> binding : step.getInputBindings().entrySet()) {
            resolvedParameters.put(binding.getKey(), resolveBinding(binding.getValue(), completedSteps));
        }
        List<String> dependencyStepOutputs = step.getDependsOn().stream()
            .map(completedSteps::get)
            .filter(Objects::nonNull)
            .map(StepExecutionResult::getOutputText)
            .filter(Objects::nonNull)
            .toList();
        Map<String, Map<String, Object>> dependencyOutputs = new LinkedHashMap<>();
        for (String dependencyId : step.getDependsOn()) {
            StepExecutionResult dependencyResult = completedSteps.get(dependencyId);
            if (dependencyResult != null) {
                dependencyOutputs.put(dependencyId, new LinkedHashMap<>(dependencyResult.getOutputData()));
            }
        }
        return new StepExecutionContext(resolvedParameters, priorOutputs, dependencyStepOutputs, dependencyOutputs);
    }

    private Object resolveBinding(String template, Map<String, StepExecutionResult> completedSteps) {
        Matcher matcher = BINDING_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        matcher.reset();
        if (matcher.matches()) {
            return resolveToken(matcher.group(1), completedSteps);
        }
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object resolved = resolveToken(matcher.group(1), completedSteps);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(resolved)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Object resolveToken(String token, Map<String, StepExecutionResult> completedSteps) {
        String[] path = token.split("\\.");
        if (path.length == 0) {
            throw new IllegalArgumentException("Invalid step binding token: " + token);
        }
        StepExecutionResult stepResult = completedSteps.get(path[0]);
        if (stepResult == null) {
            throw new IllegalArgumentException("Unknown step dependency in binding: " + path[0]);
        }
        Object current = stepResult.getOutputData();
        for (int i = 1; i < path.length; i++) {
            if (!(current instanceof Map<?, ?> currentMap) || !currentMap.containsKey(path[i])) {
                throw new IllegalArgumentException("Cannot resolve binding token: " + token);
            }
            current = currentMap.get(path[i]);
        }
        return current;
    }

    private String serialize(Plan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize plan for session. stepCount=" + plan.getSteps().size(), e);
        }
    }

    private Plan deserializePlan(String currentPlanJson) {
        if (currentPlanJson == null || currentPlanJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(currentPlanJson, Plan.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize pending plan.", e);
        }
    }
}
