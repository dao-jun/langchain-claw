package org.monarch.langchain.claw.session;

import java.time.Instant;
import org.monarch.langchain.claw.common.SessionState;

public class UserSession {

    private Long id;
    private String sessionId;
    private String userId;
    private SessionState state;
    private String currentPlanJson;
    private int currentStepIndex;
    private String pendingQuestion;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public String getCurrentPlanJson() {
        return currentPlanJson;
    }

    public void setCurrentPlanJson(String currentPlanJson) {
        this.currentPlanJson = currentPlanJson;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public String getPendingQuestion() {
        return pendingQuestion;
    }

    public void setPendingQuestion(String pendingQuestion) {
        this.pendingQuestion = pendingQuestion;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
