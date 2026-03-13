package org.monarch.langchain.claw.agent;

import java.util.HashSet;
import java.util.Set;
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
    public String getName() {
        return "ruleBasedReviewAgent";
    }

    @Override
    public String getDescription() {
        return "使用规则校验计划是否具备所需 skills 和必要参数。";
    }

    @Override
    public ReviewResult review(AgentContext context) {
        Set<String> stepIds = new HashSet<>();
        for (PlanStep step : context.getPlan().getSteps()) {
            if (step.getStepId() == null || step.getStepId().isBlank()) {
                return new ReviewResult(ReviewDecision.REJECT, "计划中存在缺少 stepId 的步骤。");
            }
            if (!stepIds.add(step.getStepId())) {
                return new ReviewResult(ReviewDecision.REJECT, "计划中存在重复的 stepId: " + step.getStepId());
            }
        }
        for (PlanStep step : context.getPlan().getSteps()) {
            for (String skillName : step.getRequiredSkills()) {
                if (!skillManager.hasSkill(context.getUserId(), skillName)) {
                    return new ReviewResult(ReviewDecision.REJECT, "缺少所需 skill: " + skillName);
                }
            }
            for (String dependencyStepId : step.getDependsOn()) {
                if (!stepIds.contains(dependencyStepId)) {
                    return new ReviewResult(ReviewDecision.REJECT, "步骤依赖不存在: " + dependencyStepId);
                }
                if (dependencyStepId.equals(step.getStepId())) {
                    return new ReviewResult(ReviewDecision.REJECT, "步骤不能依赖自身: " + dependencyStepId);
                }
            }
            if ("weather".equals(step.getExecutorType()) && String.valueOf(step.getParameters().getOrDefault("city", "")).isBlank()) {
                return new ReviewResult(ReviewDecision.ASK_USER, "请告诉我要查询哪个城市的天气。");
            }
        }
        return new ReviewResult(ReviewDecision.ACCEPT, "计划审核通过。");
    }
}
