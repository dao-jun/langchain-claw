package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.monarch.langchain.claw.models.Plan;

public interface ReviewAgentService {
    @SystemMessage("你是一个审核专家。审核给定的计划是否合理、安全、可行。如果合理，回复 'ACCEPT'；如果需修改，回复 'REJECT: 原因'；如果需要用户确认，回复 'ASK_USER: 问题'。")
    @UserMessage("计划：{{plan}}")
    String reviewPlan(@UserMessage("plan") Plan plan);
}
