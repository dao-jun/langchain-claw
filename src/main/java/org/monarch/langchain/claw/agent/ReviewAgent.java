package org.monarch.langchain.claw.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.monarch.langchain.claw.models.SessionState;

public class ReviewAgent implements Agent {
    private final ReviewAgentService service;

    public ReviewAgent(ChatLanguageModel model) {
        this.service = AiServices.create(ReviewAgentService.class, model);
    }

    @Override
    public void execute(UserSession session, String userMessage) {
        String reviewResult = service.reviewPlan(session.currentPlan);
        if (reviewResult.startsWith("ACCEPT")) {
            session.state = SessionState.PLAN_REVIEWED;
            session.shortTermMemory.add("计划审核通过。");
        } else if (reviewResult.startsWith("REJECT")) {
            // 拒绝，返回 PlanAgent 重新生成
            session.state = SessionState.INIT; // 重新开始
            session.shortTermMemory.add("计划被拒绝：" + reviewResult);
        } else if (reviewResult.startsWith("ASK_USER")) {
            // 需要用户介入
            session.state = SessionState.WAITING_FOR_USER;
            String question = reviewResult.substring("ASK_USER:".length()).trim();
            session.onUserInputNeeded = () -> {
                // 当用户回复时，会根据新消息再次进入流程
                session.shortTermMemory.add("需要用户确认：" + question);
            };
        }
    }
}
