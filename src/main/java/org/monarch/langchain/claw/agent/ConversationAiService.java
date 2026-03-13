package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ConversationAiService {

    @SystemMessage(fromResource = "prompts/conversation-executor-system.txt")
    @UserMessage(fromResource = "prompts/conversation-executor-user.txt")
    String respond(@V("skillPrompt") String skillPrompt,
                   @V("userMessage") String userMessage,
                   @V("resolvedParametersJson") String resolvedParametersJson,
                   @V("shortTermMessagesJson") String shortTermMessagesJson,
                   @V("memoriesJson") String memoriesJson,
                   @V("priorStepOutputsJson") String priorStepOutputsJson,
                   @V("dependencyStepOutputsJson") String dependencyStepOutputsJson,
                   @V("dependencyOutputsJson") String dependencyOutputsJson);
}
