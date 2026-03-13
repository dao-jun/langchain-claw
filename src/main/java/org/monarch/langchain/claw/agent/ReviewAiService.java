package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReviewAiService {

    @SystemMessage(fromResource = "prompts/review-agent-system.txt")
    @UserMessage(fromResource = "prompts/review-agent-user.txt")
    String reviewPlan(@V("userMessage") String userMessage,
                      @V("skillsJson") String skillsJson,
                      @V("planJson") String planJson);
}
