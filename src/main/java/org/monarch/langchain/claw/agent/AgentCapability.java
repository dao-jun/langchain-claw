package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.List;

public class AgentCapability {

    private List<String> requiredTools = new ArrayList<>();
    private List<String> requiredSkills = new ArrayList<>();
    private int maxRetries;
    private long timeoutMs;
    private boolean supportsParallel;

    public List<String> getRequiredTools() {
        return requiredTools;
    }

    public void setRequiredTools(List<String> requiredTools) {
        this.requiredTools = requiredTools;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isSupportsParallel() {
        return supportsParallel;
    }

    public void setSupportsParallel(boolean supportsParallel) {
        this.supportsParallel = supportsParallel;
    }
}
