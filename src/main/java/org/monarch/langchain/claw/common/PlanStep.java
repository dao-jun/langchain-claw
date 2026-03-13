package org.monarch.langchain.claw.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanStep {

    private String stepId;
    private String description;
    private String executorType;
    private List<String> requiredSkills = new ArrayList<>();
    private List<String> dependsOn = new ArrayList<>();
    private Map<String, Object> parameters = new HashMap<>();
    private Map<String, String> inputBindings = new HashMap<>();
    private Map<String, Object> output = new HashMap<>();
    private PlanStepStatus status = PlanStepStatus.PENDING;
    private String result;
    private String error;
    private int retryCount;
    private Integer maxRetries;
    private PlanStep fallbackStep;
    private List<PlanStep> compensationSteps = new ArrayList<>();
    private String condition;
    private boolean conditionMatched = true;

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExecutorType() {
        return executorType;
    }

    public void setExecutorType(String executorType) {
        this.executorType = executorType;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getInputBindings() {
        return inputBindings;
    }

    public void setInputBindings(Map<String, String> inputBindings) {
        this.inputBindings = inputBindings;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public PlanStepStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStepStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public PlanStep getFallbackStep() {
        return fallbackStep;
    }

    public void setFallbackStep(PlanStep fallbackStep) {
        this.fallbackStep = fallbackStep;
    }

    public List<PlanStep> getCompensationSteps() {
        return compensationSteps;
    }

    public void setCompensationSteps(List<PlanStep> compensationSteps) {
        this.compensationSteps = compensationSteps;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isConditionMatched() {
        return conditionMatched;
    }

    public void setConditionMatched(boolean conditionMatched) {
        this.conditionMatched = conditionMatched;
    }
}
