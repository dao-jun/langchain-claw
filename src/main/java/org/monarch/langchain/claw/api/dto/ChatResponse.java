package org.monarch.langchain.claw.api.dto;

import java.util.ArrayList;
import java.util.List;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.SessionState;
import org.monarch.langchain.claw.config.ModelSelection;
import org.monarch.langchain.claw.skill.SkillDefinition;

public class ChatResponse {

    private String sessionId;
    private SessionState state;
    private String message;
    private Plan plan;
    private List<String> stepOutputs = new ArrayList<>();
    private List<SkillDefinition> activeSkills = new ArrayList<>();
    private List<ModelSelection> activeModels = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public List<String> getStepOutputs() {
        return stepOutputs;
    }

    public void setStepOutputs(List<String> stepOutputs) {
        this.stepOutputs = stepOutputs;
    }

    public List<SkillDefinition> getActiveSkills() {
        return activeSkills;
    }

    public void setActiveSkills(List<SkillDefinition> activeSkills) {
        this.activeSkills = activeSkills;
    }

    public List<ModelSelection> getActiveModels() {
        return activeModels;
    }

    public void setActiveModels(List<ModelSelection> activeModels) {
        this.activeModels = activeModels;
    }
}
