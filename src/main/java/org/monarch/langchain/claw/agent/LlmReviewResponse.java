package org.monarch.langchain.claw.agent;

public class LlmReviewResponse {

    private String decision;
    private String message;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
