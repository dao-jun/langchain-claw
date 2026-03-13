package org.monarch.langchain.claw.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.monarch.langchain.claw.agent.AgentContext;
import org.monarch.langchain.claw.agent.AgentExecutionResult;
import org.monarch.langchain.claw.agent.AgentType;
import org.monarch.langchain.claw.agent.ExecutorAgentRegistry;
import org.monarch.langchain.claw.agent.PlanAgent;
import org.monarch.langchain.claw.agent.ReviewAgent;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
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
        List<String> outputs = new ArrayList<>();
        for (PlanStep step : plan.getSteps()) {
            outputs.addAll(executeStep(userId, session, message, skills, shortTermMessages, memories, plan, step, outputs, requestId, traceId));
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

    private List<String> executeStep(String userId,
                                     UserSession session,
                                     String message,
                                     List<SkillDefinition> skills,
                                     List<String> shortTermMessages,
                                     List<MemorySnippet> memories,
                                     Plan plan,
                                     PlanStep step,
                                     List<String> priorOutputs,
                                     String requestId,
                                     String traceId) {
        Plan singleStepPlan = new Plan();
        singleStepPlan.setRationale(plan.getRationale());
        PlanStep executionStep = copyStep(step);
        if ("conversation".equals(step.getExecutorType()) && !priorOutputs.isEmpty()) {
            // Conversation steps receive earlier step outputs so follow-up explanation/summarization
            // requests can respond with the task results that were already produced in this plan.
            executionStep.getParameters().put("priorStepOutputs", new ArrayList<>(priorOutputs));
        }
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
        AgentExecutionResult executionResult = executorAgentRegistry.getExecutor(step.getExecutorType()).execute(executionContext).join();
        step.setResult(executionStep.getResult());
        step.setError(executionStep.getError());
        return executionResult.getStepOutputs();
    }

    private PlanStep copyStep(PlanStep source) {
        PlanStep copy = new PlanStep();
        copy.setDescription(source.getDescription());
        copy.setExecutorType(source.getExecutorType());
        copy.setRequiredSkills(new ArrayList<>(source.getRequiredSkills()));
        copy.setParameters(new java.util.HashMap<>(source.getParameters()));
        copy.setResult(source.getResult());
        copy.setError(source.getError());
        return copy;
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
