package org.monarch.langchain.claw.agent;

import java.util.LinkedHashMap;
import java.util.Map;

public class StepExecutionResult {

    private final String outputText;
    private final Map<String, Object> outputData;

    public StepExecutionResult(String outputText, Map<String, Object> outputData) {
        this.outputText = outputText;
        this.outputData = new LinkedHashMap<>(outputData);
    }

    public String getOutputText() {
        return outputText;
    }

    public Map<String, Object> getOutputData() {
        return outputData;
    }
}
