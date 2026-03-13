package org.monarch.langchain.claw.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.monarch.langchain.claw.models.Plan;
import org.monarch.langchain.claw.models.SessionState;

public class PlanAgent implements Agent {
    private final PlanAgentService service;

    public PlanAgent(ChatLanguageModel model) {
        this.service = AiServices.create(PlanAgentService.class, model);
    }

    @Override
    public void execute(UserSession session, String userMessage) {
        Plan plan = service.generatePlan(userMessage);
        session.currentPlan = plan;
        session.state = SessionState.PLAN_GENERATED;
        session.shortTermMemory.add(userMessage); // 记录用户消息
        session.shortTermMemory.add("计划已生成：" + plan.steps); // 记录系统消息
    }
}
