package org.monarch.langchain.claw.agent;

import org.monarch.langchain.claw.common.PlanStep;
import org.monarch.langchain.claw.skill.SkillManager;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedReviewAgent implements ReviewAgent {

    private final SkillManager skillManager;

    public RuleBasedReviewAgent(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public ReviewResult review(AgentContext context) {
        for (PlanStep step : context.getPlan().getSteps()) {
            for (String skillName : step.getRequiredSkills()) {
                if (!skillManager.hasSkill(context.getUserId(), skillName)) {
                    return new ReviewResult(ReviewDecision.REJECT, "缺少所需 skill: " + skillName);
                }
            }
            if ("weather".equals(step.getExecutorType()) && String.valueOf(step.getParameters().getOrDefault("city", "")).isBlank()) {
                return new ReviewResult(ReviewDecision.ASK_USER, "请告诉我要查询哪个城市的天气。");
            }
        }
        return new ReviewResult(ReviewDecision.ACCEPT, "计划审核通过。");
    }
}
