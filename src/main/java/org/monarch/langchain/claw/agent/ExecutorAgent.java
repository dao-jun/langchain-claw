package org.monarch.langchain.claw.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.monarch.langchain.claw.models.PlanStep;
import org.monarch.langchain.claw.models.SessionState;

public class ExecutorAgent implements Agent {
    private final ExecutorAgentService service;

    public ExecutorAgent(ChatLanguageModel model, Object... tools) {
        this.service = AiServices.builder(ExecutorAgentService.class)
                .chatLanguageModel(model)
                .tools(tools)
                .build();
    }

    @Override
    public void execute(UserSession session, String userMessage) {
        if (session.currentPlan == null || session.currentStepIndex >= session.currentPlan.steps.size()) {
            session.state = SessionState.COMPLETED;
            return;
        }

        PlanStep step = session.currentPlan.steps.get(session.currentStepIndex);
        try {
            String result = service.executeStep(step, session.context);
            step.result = result;
            session.context.put("last_step_result", result);
            session.shortTermMemory.add("步骤执行成功：" + step.description + " -> " + result);
            session.currentStepIndex++;
            if (session.currentStepIndex >= session.currentPlan.steps.size()) {
                session.state = SessionState.COMPLETED;
            } else {
                session.state = SessionState.EXECUTING; // 继续下一步
            }
        } catch (Exception e) {
            step.error = e.getMessage();
            session.shortTermMemory.add("步骤执行失败：" + step.description + "，错误：" + e.getMessage());
            // 失败后交由 ReviewAgent 决定如何处理
            session.state = SessionState.PLAN_GENERATED; // 重新计划或修改
        }
    }
}
