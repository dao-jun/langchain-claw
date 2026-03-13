package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StepExecutionContext {

    private final Map<String, Object> resolvedParameters;
    private final List<String> priorStepOutputs;
    private final List<String> dependencyStepOutputs;
    private final Map<String, Map<String, Object>> dependencyOutputs;

    public StepExecutionContext(Map<String, Object> resolvedParameters,
                                List<String> priorStepOutputs,
                                List<String> dependencyStepOutputs,
                                Map<String, Map<String, Object>> dependencyOutputs) {
        this.resolvedParameters = new LinkedHashMap<>(resolvedParameters);
        this.priorStepOutputs = new ArrayList<>(priorStepOutputs);
        this.dependencyStepOutputs = new ArrayList<>(dependencyStepOutputs);
        this.dependencyOutputs = new LinkedHashMap<>(dependencyOutputs);
    }

    public Map<String, Object> getResolvedParameters() {
        return resolvedParameters;
    }

    public List<String> getPriorStepOutputs() {
        return priorStepOutputs;
    }

    public List<String> getDependencyStepOutputs() {
        return dependencyStepOutputs;
    }

    public Map<String, Map<String, Object>> getDependencyOutputs() {
        return dependencyOutputs;
    }
}
