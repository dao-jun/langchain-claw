package org.monarch.langchain.claw.agent;

import java.util.List;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.config.ModelSelection;
import org.monarch.langchain.claw.memory.MemorySnippet;
import org.monarch.langchain.claw.session.UserSession;
import org.monarch.langchain.claw.skill.SkillDefinition;

public class AgentContext {

    private final String userId;
    private final String sessionId;
    private final String userMessage;
    private final UserSession session;
    private final List<SkillDefinition> activeSkills;
    private final List<String> shortTermMessages;
    private final List<MemorySnippet> longTermMemories;
    private final ModelSelection modelSelection;
    private final Plan plan;

    public AgentContext(String userId,
                        String sessionId,
                        String userMessage,
                        UserSession session,
                        List<SkillDefinition> activeSkills,
                        List<String> shortTermMessages,
                        List<MemorySnippet> longTermMemories,
                        ModelSelection modelSelection,
                        Plan plan) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.session = session;
        this.activeSkills = activeSkills;
        this.shortTermMessages = shortTermMessages;
        this.longTermMemories = longTermMemories;
        this.modelSelection = modelSelection;
        this.plan = plan;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public UserSession getSession() {
        return session;
    }

    public List<SkillDefinition> getActiveSkills() {
        return activeSkills;
    }

    public List<String> getShortTermMessages() {
        return shortTermMessages;
    }

    public List<MemorySnippet> getLongTermMemories() {
        return longTermMemories;
    }

    public ModelSelection getModelSelection() {
        return modelSelection;
    }

    public Plan getPlan() {
        return plan;
    }
}
