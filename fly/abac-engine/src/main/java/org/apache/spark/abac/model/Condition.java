package org.apache.spark.abac.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a condition within a rule that must be evaluated.
 */
public class Condition {
    private final String attributeName;
    private final ConditionOperator operator;
    private final Object expectedValue;
    private final String expression;

    @JsonCreator
    public Condition(
            @JsonProperty("attributeName") String attributeName,
            @JsonProperty("operator") ConditionOperator operator,
            @JsonProperty("expectedValue") Object expectedValue,
            @JsonProperty("expression") String expression) {
        this.attributeName = attributeName;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.expression = expression;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public ConditionOperator getOperator() {
        return operator;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    public String getExpression() {
        return expression;
    }

    /**
     * Checks if this condition is expression-based (uses SpEL) or attribute-based.
     */
    public boolean isExpressionBased() {
        return expression != null && !expression.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Condition condition = (Condition) o;
        return Objects.equals(attributeName, condition.attributeName) &&
                operator == condition.operator &&
                Objects.equals(expectedValue, condition.expectedValue) &&
                Objects.equals(expression, condition.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributeName, operator, expectedValue, expression);
    }

    @Override
    public String toString() {
        if (isExpressionBased()) {
            return "Condition{expression='" + expression + "'}";
        }
        return "Condition{" +
                "attributeName='" + attributeName + '\'' +
                ", operator=" + operator +
                ", expectedValue=" + expectedValue +
                '}';
    }
} 