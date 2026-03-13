package org.monarch.langchain.claw.agent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.monarch.langchain.claw.common.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.monarch.langchain.claw.skill.SkillManager;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuleBasedReviewAgent implements ReviewAgent {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedReviewAgent.class);
    private final SkillManager skillManager;
    private final LangChain4jAgentServiceFactory serviceFactory;
    private final LlmJsonSupport llmJsonSupport;

    public RuleBasedReviewAgent(SkillManager skillManager,
                                LangChain4jAgentServiceFactory serviceFactory,
                                LlmJsonSupport llmJsonSupport) {
        this.skillManager = skillManager;
        this.serviceFactory = serviceFactory;
        this.llmJsonSupport = llmJsonSupport;
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
        ReviewResult deterministicResult = reviewWithRules(context);
        if (deterministicResult.decision() != ReviewDecision.ACCEPT) {
            return deterministicResult;
        }
        if (!shouldUseLlm(context)) {
            return deterministicResult;
        }
        try {
            String rawResponse = serviceFactory.reviewService(context.getModelSelection()).reviewPlan(
                context.getUserMessage(),
                llmJsonSupport.toJson(context.getActiveSkills()),
                llmJsonSupport.toJson(context.getPlan()));
            LlmReviewResponse reviewResponse = llmJsonSupport.fromJsonObject(rawResponse, LlmReviewResponse.class);
            ReviewDecision decision = ReviewDecision.valueOf(reviewResponse.getDecision().trim().toUpperCase(Locale.ROOT));
            String message = StringUtils.hasText(reviewResponse.getMessage()) ? reviewResponse.getMessage() : deterministicResult.message();
            return new ReviewResult(decision, message);
        } catch (RuntimeException ex) {
            log.warn("Falling back to rule-based review. provider={}, model={}, error={}",
                context.getModelSelection().getProvider(),
                context.getModelSelection().getModelName(),
                ex.getMessage());
            return deterministicResult;
        }
    }

    private ReviewResult reviewWithRules(AgentContext context) {
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

    private boolean shouldUseLlm(AgentContext context) {
        String provider = context.getModelSelection() == null ? null : context.getModelSelection().getProvider();
        return StringUtils.hasText(provider) && !"rule-based".equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
