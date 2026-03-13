package org.monarch.langchain.claw.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.monarch.langchain.claw.common.Plan;
import org.monarch.langchain.claw.common.PlanStep;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedPlanAgent implements PlanAgent {

    @Override
    public String getName() {
        return "ruleBasedPlanAgent";
    }

    @Override
    public String getDescription() {
        return "使用规则从用户消息中生成可执行计划。";
    }

    private static final Pattern CALCULATION_EXPRESSION_PATTERN = Pattern.compile("([0-9()\\s+\\-*/.]+)");
    private static final Pattern WEATHER_CITY_PATTERN = Pattern.compile("(?:天气|weather)(?:是|怎么样|如何|查询)?([\\u4e00-\\u9fa5A-Za-z]+)?");
    private static final List<String> KNOWN_CITIES = List.of(
        "北京", "上海", "广州", "深圳", "杭州", "南京", "苏州", "成都", "武汉", "西安", "天津", "重庆", "长沙", "青岛", "厦门");
    private static final List<String> CONVERSATION_CUES = List.of("解释", "说明", "总结", "分析", "建议", "评价", "聊", "说一句", "说说");

    @Override
    public Plan generatePlan(AgentContext context) {
        Plan resumedPlan = resumePendingPlan(context);
        if (resumedPlan != null) {
            return resumedPlan;
        }

        String message = context.getUserMessage();
        Plan plan = new Plan();
        List<PlanStep> steps = new ArrayList<>();
        plan.setRationale("根据用户消息拆分为可执行步骤，并结合可用 skills 与模型配置进行执行。");
        int calculatorIndex = 1;
        int weatherIndex = 1;
        int conversationIndex = 1;

        if (containsCalculationIntent(message)) {
            PlanStep calculator = createCalculatorStep("执行计算任务", "calculator-" + calculatorIndex++, extractExpression(message));
            steps.add(calculator);
        }

        List<PlanStep> weatherSteps = new ArrayList<>();
        if (containsWeatherIntent(message)) {
            List<String> cities = extractCities(message);
            if (cities.isEmpty()) {
                PlanStep weather = createWeatherStep("", "weather-" + weatherIndex++);
                steps.add(weather);
                weatherSteps.add(weather);
            } else {
                for (String city : cities) {
                    PlanStep weather = createWeatherStep(city, "weather-" + weatherIndex++);
                    steps.add(weather);
                    weatherSteps.add(weather);
                }
            }
        }

        if (shouldAddAverageTemperatureStep(message, weatherSteps)) {
            steps.add(createAverageTemperatureStep(weatherSteps, "calculator-" + calculatorIndex++));
        }

        if (steps.isEmpty() || shouldAddConversationStep(message, steps)) {
            steps.add(createConversationStep(message, "conversation-" + conversationIndex++,
                steps.stream().map(PlanStep::getStepId).toList()));
        }

        plan.setSteps(steps);
        return plan;
    }

    private Plan resumePendingPlan(AgentContext context) {
        if (context.getPlan() == null || context.getSession() == null || context.getSession().getState() == null) {
            return null;
        }
        List<PlanStep> missingWeatherSteps = context.getPlan().getSteps().stream()
            .filter(step -> "weather".equals(step.getExecutorType()))
            .filter(step -> String.valueOf(step.getParameters().getOrDefault("city", "")).isBlank())
            .toList();
        if (missingWeatherSteps.isEmpty()) {
            return null;
        }
        List<String> cities = extractCities(context.getUserMessage());
        if (cities.isEmpty()) {
            String city = extractCity(context.getUserMessage());
            if (!city.isBlank()) {
                cities = List.of(city);
            }
        }
        if (cities.isEmpty()) {
            return null;
        }
        for (int i = 0; i < missingWeatherSteps.size() && i < cities.size(); i++) {
            missingWeatherSteps.get(i).getParameters().put("city", cities.get(i));
        }
        return context.getPlan();
    }

    private boolean containsCalculationIntent(String message) {
        String expression = extractExpression(message);
        return message.toLowerCase().contains("calculate")
            || message.matches(".*[0-9]+\\s*[+\\-*/].*")
            || (message.contains("计算") && isCalculableExpression(expression));
    }

    private boolean containsWeatherIntent(String message) {
        return message.contains("天气") || message.toLowerCase().contains("weather");
    }

    private String extractExpression(String message) {
        Matcher matcher = CALCULATION_EXPRESSION_PATTERN.matcher(message);
        String candidate = null;
        while (matcher.find()) {
            String matched = matcher.group(1).trim();
            if (isCalculableExpression(matched)) {
                candidate = matched;
            }
        }
        return candidate == null ? "0" : candidate;
    }

    private String extractCity(String message) {
        for (String city : KNOWN_CITIES) {
            if (message.contains(city)) {
                return city;
            }
        }
        Matcher matcher = WEATHER_CITY_PATTERN.matcher(message);
        if (matcher.find() && matcher.groupCount() > 0 && matcher.group(1) != null && !matcher.group(1).isBlank()) {
            return matcher.group(1);
        }
        return "";
    }

    private List<String> extractCities(String message) {
        Set<String> cities = new LinkedHashSet<>();
        for (String city : KNOWN_CITIES) {
            if (message.contains(city)) {
                cities.add(city);
            }
        }
        if (!cities.isEmpty()) {
            return new ArrayList<>(cities);
        }
        String singleCity = extractCity(message);
        return singleCity.isBlank() ? List.of() : List.of(singleCity);
    }

    private boolean shouldAddConversationStep(String message, List<PlanStep> existingSteps) {
        // Append conversation step if request has tasks and contains explanation cues.
        return !existingSteps.isEmpty() && CONVERSATION_CUES.stream().anyMatch(message::contains);
    }

    private boolean shouldAddAverageTemperatureStep(String message, List<PlanStep> weatherSteps) {
        return weatherSteps.size() >= 2
            && (message.contains("平均温度")
                || message.contains("平均气温")
                || message.contains("平均一下")
                || message.toLowerCase().contains("average temperature"));
    }

    private PlanStep createWeatherStep(String city, String stepId) {
        PlanStep weather = new PlanStep();
        weather.setStepId(stepId);
        weather.setDescription(city.isBlank() ? "查询天气" : "查询" + city + "天气");
        weather.setExecutorType("weather");
        weather.setRequiredSkills(List.of("weather"));
        weather.setParameters(new HashMap<>());
        weather.getParameters().put("city", city);
        return weather;
    }

    private PlanStep createCalculatorStep(String description, String stepId, String expression) {
        PlanStep calculator = new PlanStep();
        calculator.setStepId(stepId);
        calculator.setDescription(description);
        calculator.setExecutorType("calculator");
        calculator.setRequiredSkills(List.of("calculator"));
        calculator.setParameters(new HashMap<>());
        calculator.getParameters().put("expression", expression);
        return calculator;
    }

    private PlanStep createAverageTemperatureStep(List<PlanStep> weatherSteps, String stepId) {
        String expressionTemplate = weatherSteps.stream()
            .map(step -> "${" + step.getStepId() + ".temperatureC}")
            .reduce((left, right) -> left + " + " + right)
            .map(sum -> "(" + sum + ") / " + weatherSteps.size())
            .orElse("0");
        PlanStep calculator = createCalculatorStep("计算多城市平均温度", stepId, "0");
        calculator.setDependsOn(weatherSteps.stream().map(PlanStep::getStepId).toList());
        calculator.setInputBindings(new HashMap<>());
        calculator.getInputBindings().put("expression", expressionTemplate);
        return calculator;
    }

    private PlanStep createConversationStep(String message, String stepId, List<String> dependsOn) {
        PlanStep conversation = new PlanStep();
        conversation.setStepId(stepId);
        conversation.setDescription("进行通用对话响应");
        conversation.setExecutorType("conversation");
        conversation.setRequiredSkills(List.of("conversation"));
        conversation.setDependsOn(new ArrayList<>(dependsOn));
        conversation.setParameters(new HashMap<>());
        conversation.getParameters().put("message", message);
        return conversation;
    }

    private boolean isCalculableExpression(String value) {
        return value.matches("[()\\d\\s+\\-*/.]+") && value.matches(".*\\d.*[+\\-*/].*\\d.*");
    }
}
