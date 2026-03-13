package org.monarch.langchain.claw.tool.impl;

import java.util.Map;
import org.monarch.langchain.claw.tool.ToolHandler;
import org.springframework.stereotype.Component;

@Component
public class CalculatorToolHandler implements ToolHandler {

    private static final double EPSILON = 1e-9;

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String execute(Map<String, Object> input) {
        String expression = String.valueOf(input.getOrDefault("expression", "0"));
        double value = new ExpressionParser(expression).parse();
        long longValue = (long) value;
        String rendered = Math.abs(value - longValue) < EPSILON ? Long.toString(longValue) : Double.toString(value);
        return expression + " = " + rendered;
    }

    static class ExpressionParser {
        private final String expression;
        private int index;

        ExpressionParser(String expression) {
            this.expression = expression.replaceAll("\\s+", "");
        }

        double parse() {
            double value = parseExpression();
            if (index != expression.length()) {
                throw new IllegalArgumentException("Invalid expression: " + expression);
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (index < expression.length()) {
                char operator = expression.charAt(index);
                if (operator != '+' && operator != '-') {
                    break;
                }
                index++;
                double rhs = parseTerm();
                value = operator == '+' ? value + rhs : value - rhs;
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (index < expression.length()) {
                char operator = expression.charAt(index);
                if (operator != '*' && operator != '/') {
                    break;
                }
                index++;
                double rhs = parseFactor();
                value = operator == '*' ? value * rhs : value / rhs;
            }
            return value;
        }

        private double parseFactor() {
            if (index >= expression.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            char current = expression.charAt(index);
            if (current == '(') {
                index++;
                double value = parseExpression();
                if (index >= expression.length() || expression.charAt(index) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                index++;
                return value;
            }
            int start = index;
            if (current == '+' || current == '-') {
                index++;
            }
            int dots = 0;
            while (index < expression.length() && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) {
                if (expression.charAt(index) == '.' && ++dots > 1) {
                    throw new IllegalArgumentException("Invalid decimal number in expression: " + expression);
                }
                index++;
            }
            return Double.parseDouble(expression.substring(start, index));
        }
    }
}
