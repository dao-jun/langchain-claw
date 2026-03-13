package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.List;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.SessionState;

public class AgentExecutionResult {

    private String sessionId;
    private SessionState nextState;
    private String message;
    private Plan plan;
    private String requestId;
    private String traceId;
    private List<String> stepOutputs = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public SessionState getNextState() {
        return nextState;
    }

    public void setNextState(SessionState nextState) {
        this.nextState = nextState;
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public List<String> getStepOutputs() {
        return stepOutputs;
    }

    public void setStepOutputs(List<String> stepOutputs) {
        this.stepOutputs = stepOutputs;
    }
}
