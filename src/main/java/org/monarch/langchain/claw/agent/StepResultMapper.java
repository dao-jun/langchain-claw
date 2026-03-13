package org.monarch.langchain.claw.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.monarch.langchain.claw.common.PlanStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StepResultMapper {

    private static final Pattern WEATHER_TEMPERATURE_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)°C");
    private static final Logger log = LoggerFactory.getLogger(StepResultMapper.class);

    public Map<String, Object> map(PlanStep step, Map<String, Object> resolvedParameters, String outputText) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("text", outputText);
        output.put("executorType", step.getExecutorType());
        switch (step.getExecutorType()) {
            case "calculator" -> populateCalculatorOutput(output, resolvedParameters, outputText);
            case "weather" -> populateWeatherOutput(output, resolvedParameters, outputText);
            case "conversation" -> output.put("response", outputText);
            default -> output.putAll(resolvedParameters);
        }
        return output;
    }

    private void populateCalculatorOutput(Map<String, Object> output,
                                          Map<String, Object> resolvedParameters,
                                          String outputText) {
        String expression = String.valueOf(resolvedParameters.getOrDefault("expression", ""));
        output.put("expression", expression);
        String rendered = outputText.contains("=") ? outputText.substring(outputText.indexOf('=') + 1).trim() : outputText;
        output.put("renderedResult", rendered);
        try {
            output.put("numericValue", Double.parseDouble(rendered));
        } catch (NumberFormatException ex) {
            log.debug("Failed to parse calculator output as numeric value. outputText={}", outputText, ex);
        }
    }

    private void populateWeatherOutput(Map<String, Object> output,
                                       Map<String, Object> resolvedParameters,
                                       String outputText) {
        output.put("city", String.valueOf(resolvedParameters.getOrDefault("city", "")));
        Matcher matcher = WEATHER_TEMPERATURE_PATTERN.matcher(outputText);
        if (matcher.find()) {
            output.put("temperatureC", Double.parseDouble(matcher.group(1)));
        }
    }
}
