package org.monarch.langchain.claw.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
        UserSession session = sessionManager.getOrCreate(userId, maybeSessionId);
        sessionManager.appendMessage(session.getSessionId(), "user", message);
        List<SkillDefinition> skills = skillManager.getAvailableSkills(userId);
        List<String> shortTermMessages = sessionManager.recentMessages(session.getSessionId(), 20);
        List<MemorySnippet> memories = memoryManager.recall(userId, message, 5);

        AgentContext planningContext = new AgentContext(
            userId,
            session.getSessionId(),
            message,
            session,
            skills,
            shortTermMessages,
            memories,
            modelConfigService.resolve(userId, AgentType.PLAN),
            null);
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
            plan);
        AgentExecutionResult review = reviewAgent.execute(reviewContext).join();

        AgentExecutionResult result = new AgentExecutionResult();
        result.setSessionId(session.getSessionId());
        result.setPlan(plan);
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
        List<String> outputs = plan.getSteps().stream()
            .map(step -> executeStep(userId, session, message, skills, shortTermMessages, memories, plan, step))
            .flatMap(List::stream)
            .toList();
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
                                     PlanStep step) {
        Plan singleStepPlan = new Plan();
        singleStepPlan.setRationale(plan.getRationale());
        singleStepPlan.setSteps(List.of(step));
        AgentContext executionContext = new AgentContext(
            userId,
            session.getSessionId(),
            message,
            session,
            skills,
            shortTermMessages,
            memories,
            modelConfigService.resolve(userId, AgentType.EXECUTOR),
            singleStepPlan);
        return executorAgentRegistry.getExecutor(step.getExecutorType()).execute(executionContext).join().getStepOutputs();
    }

    private String serialize(Plan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize plan for session. stepCount=" + plan.getSteps().size(), e);
        }
    }
}
