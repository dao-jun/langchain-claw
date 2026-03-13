package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.monarch.langchain.claw.models.Plan;

public interface PlanAgentService {
    @SystemMessage("你是一个计划制定专家。根据用户请求，生成一个合理的执行计划，包含多个步骤。每个步骤应有清晰描述和所需执行器类型。")
    @UserMessage("用户请求：{{it}}")
    Plan generatePlan(String userRequest);
}
