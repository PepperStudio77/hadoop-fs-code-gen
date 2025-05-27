package org.apache.spark.abac.impl;

import org.apache.spark.abac.engine.ConditionEvaluator;
import org.apache.spark.abac.engine.ConditionEvaluationResult;
import org.apache.spark.abac.model.Condition;
import org.apache.spark.abac.model.ConditionOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Default implementation of ConditionEvaluator that handles both attribute-based
 * and expression-based conditions.
 */
public class DefaultConditionEvaluator implements ConditionEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(DefaultConditionEvaluator.class);
    
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Override
    public ConditionEvaluationResult evaluate(Condition condition, Map<String, Object> attributes) {
        try {
            if (condition.isExpressionBased()) {
                return evaluateExpression(condition, attributes);
            } else {
                return evaluateAttribute(condition, attributes);
            }
        } catch (Exception e) {
            logger.error("Error evaluating condition: {}", condition, e);
            return new ConditionEvaluationResult(false, "Evaluation error: " + e.getMessage(), null, condition.getExpectedValue());
        }
    }

    /**
     * Evaluates expression-based conditions using Spring Expression Language (SpEL).
     */
    private ConditionEvaluationResult evaluateExpression(Condition condition, Map<String, Object> attributes) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            
            // Add all attributes to the expression context
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                context.setVariable(entry.getKey().replace(".", "_"), entry.getValue());
            }
            
            // Also add flattened attributes (user.department becomes user_department)
            attributes.forEach((key, value) -> {
                if (key.contains(".")) {
                    context.setVariable(key.replace(".", "_"), value);
                }
            });
            
            Expression expression = expressionParser.parseExpression(condition.getExpression());
            Object result = expression.getValue(context);
            
            boolean satisfied = Boolean.TRUE.equals(result);
            String reason = satisfied ? "Expression evaluated to true" : "Expression evaluated to false";
            
            return new ConditionEvaluationResult(satisfied, reason, result, true);
            
        } catch (Exception e) {
            logger.error("Error evaluating expression: {}", condition.getExpression(), e);
            return new ConditionEvaluationResult(false, "Expression evaluation failed: " + e.getMessage(), null, true);
        }
    }

    /**
     * Evaluates attribute-based conditions using comparison operators.
     */
    private ConditionEvaluationResult evaluateAttribute(Condition condition, Map<String, Object> attributes) {
        Object actualValue = attributes.get(condition.getAttributeName());
        Object expectedValue = condition.getExpectedValue();
        ConditionOperator operator = condition.getOperator();

        if (actualValue == null) {
            return new ConditionEvaluationResult(false, "Attribute not found: " + condition.getAttributeName(), null, expectedValue);
        }

        boolean satisfied = evaluateOperator(actualValue, expectedValue, operator);
        String reason = String.format("Attribute %s %s %s: %s", 
            condition.getAttributeName(), operator, expectedValue, satisfied ? "satisfied" : "not satisfied");

        return new ConditionEvaluationResult(satisfied, reason, actualValue, expectedValue);
    }

    /**
     * Evaluates different comparison operators.
     */
    private boolean evaluateOperator(Object actualValue, Object expectedValue, ConditionOperator operator) {
        switch (operator) {
            case EQUALS:
                return actualValue.equals(expectedValue);
            
            case NOT_EQUALS:
                return !actualValue.equals(expectedValue);
            
            case GREATER_THAN:
                return compareNumbers(actualValue, expectedValue) > 0;
            
            case GREATER_THAN_OR_EQUAL:
                return compareNumbers(actualValue, expectedValue) >= 0;
            
            case LESS_THAN:
                return compareNumbers(actualValue, expectedValue) < 0;
            
            case LESS_THAN_OR_EQUAL:
                return compareNumbers(actualValue, expectedValue) <= 0;
            
            case CONTAINS:
                return actualValue.toString().contains(expectedValue.toString());
            
            case NOT_CONTAINS:
                return !actualValue.toString().contains(expectedValue.toString());
            
            case STARTS_WITH:
                return actualValue.toString().startsWith(expectedValue.toString());
            
            case ENDS_WITH:
                return actualValue.toString().endsWith(expectedValue.toString());
            
            case MATCHES:
                Pattern pattern = Pattern.compile(expectedValue.toString());
                return pattern.matcher(actualValue.toString()).matches();
            
            case IN:
                if (expectedValue instanceof Collection) {
                    return ((Collection<?>) expectedValue).contains(actualValue);
                } else if (expectedValue instanceof List) {
                    return ((List<?>) expectedValue).contains(actualValue);
                }
                return false;
            
            case NOT_IN:
                if (expectedValue instanceof Collection) {
                    return !((Collection<?>) expectedValue).contains(actualValue);
                } else if (expectedValue instanceof List) {
                    return !((List<?>) expectedValue).contains(actualValue);
                }
                return true;
            
            default:
                logger.warn("Unknown operator: {}", operator);
                return false;
        }
    }

    /**
     * Compares two values as numbers.
     */
    private int compareNumbers(Object actual, Object expected) {
        try {
            if (actual instanceof Number && expected instanceof Number) {
                double actualDouble = ((Number) actual).doubleValue();
                double expectedDouble = ((Number) expected).doubleValue();
                return Double.compare(actualDouble, expectedDouble);
            } else {
                // Try to parse as strings
                double actualDouble = Double.parseDouble(actual.toString());
                double expectedDouble = Double.parseDouble(expected.toString());
                return Double.compare(actualDouble, expectedDouble);
            }
        } catch (NumberFormatException e) {
            logger.error("Cannot compare non-numeric values: {} and {}", actual, expected);
            throw new IllegalArgumentException("Cannot compare non-numeric values");
        }
    }
} 