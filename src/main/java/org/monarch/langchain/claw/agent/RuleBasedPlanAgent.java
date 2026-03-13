package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedPlanAgent implements PlanAgent {

    private static final Pattern EXPRESSION = Pattern.compile("([0-9()\\s+\\-*/.]+)");
    private static final Pattern CITY = Pattern.compile("(?:天气|weather)(?:是|怎么样|如何|查询)?([\\u4e00-\\u9fa5A-Za-z]+)?");

    @Override
    public Plan generatePlan(AgentContext context) {
        String message = context.getUserMessage();
        Plan plan = new Plan();
        List<PlanStep> steps = new ArrayList<>();
        plan.setRationale("根据用户消息拆分为可执行步骤，并结合可用 skills 与模型配置进行执行。");

        if (containsCalculationIntent(message)) {
            PlanStep calculator = new PlanStep();
            calculator.setDescription("执行计算任务");
            calculator.setExecutorType("calculator");
            calculator.setRequiredSkills(List.of("calculator"));
            calculator.setParameters(new HashMap<>());
            calculator.getParameters().put("expression", extractExpression(message));
            steps.add(calculator);
        }

        if (containsWeatherIntent(message)) {
            PlanStep weather = new PlanStep();
            weather.setDescription("查询天气");
            weather.setExecutorType("weather");
            weather.setRequiredSkills(List.of("weather"));
            weather.setParameters(new HashMap<>());
            weather.getParameters().put("city", extractCity(message));
            steps.add(weather);
        }

        if (steps.isEmpty()) {
            PlanStep conversation = new PlanStep();
            conversation.setDescription("进行通用对话响应");
            conversation.setExecutorType("conversation");
            conversation.setRequiredSkills(List.of("conversation"));
            conversation.setParameters(new HashMap<>());
            conversation.getParameters().put("message", message);
            steps.add(conversation);
        }

        plan.setSteps(steps);
        return plan;
    }

    private boolean containsCalculationIntent(String message) {
        return message.contains("计算") || message.toLowerCase().contains("calculate") || message.matches(".*[0-9]+\\s*[+\\-*/].*");
    }

    private boolean containsWeatherIntent(String message) {
        return message.contains("天气") || message.toLowerCase().contains("weather");
    }

    private String extractExpression(String message) {
        Matcher matcher = EXPRESSION.matcher(message);
        String candidate = null;
        while (matcher.find()) {
            String matched = matcher.group(1).trim();
            if (matched.matches(".*[+\\-*/].*")) {
                candidate = matched;
            }
        }
        return candidate == null ? "0" : candidate;
    }

    private String extractCity(String message) {
        Matcher matcher = CITY.matcher(message);
        if (matcher.find() && matcher.groupCount() > 0 && matcher.group(1) != null && !matcher.group(1).isBlank()) {
            return matcher.group(1);
        }
        if (message.contains("北京")) {
            return "北京";
        }
        if (message.contains("上海")) {
            return "上海";
        }
        return "";
    }
}
