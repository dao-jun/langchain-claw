package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ToolCallingExecutorAiService {

    @SystemMessage(fromResource = "prompts/tool-calling-executor-system.txt")
    @UserMessage(fromResource = "prompts/tool-calling-executor-user.txt")
    String execute(@V("executorType") String executorType,
                   @V("skillPrompt") String skillPrompt,
                   @V("userMessage") String userMessage,
                   @V("resolvedParametersJson") String resolvedParametersJson,
                   @V("priorStepOutputsJson") String priorStepOutputsJson,
                   @V("dependencyOutputsJson") String dependencyOutputsJson);
}
