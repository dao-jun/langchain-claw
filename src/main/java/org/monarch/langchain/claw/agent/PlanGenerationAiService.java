package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PlanGenerationAiService {

    @SystemMessage(fromResource = "prompts/plan-agent-system.txt")
    @UserMessage(fromResource = "prompts/plan-agent-user.txt")
    String generatePlan(@V("userMessage") String userMessage,
                        @V("skillsJson") String skillsJson,
                        @V("shortTermMessagesJson") String shortTermMessagesJson,
                        @V("memoriesJson") String memoriesJson,
                        @V("pendingPlanJson") String pendingPlanJson);
}
