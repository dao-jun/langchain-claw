package org.monarch.langchain.claw.agent;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutorAgentRegistry {

    private final List<ExecutorAgent> executors;

    public ExecutorAgentRegistry(List<ExecutorAgent> executors) {
        this.executors = executors;
    }

    public ExecutorAgent getExecutor(String executorType) {
        return executors.stream()
            .filter(executor -> executor.supports(executorType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No executor registered for type: " + executorType));
    }
}
