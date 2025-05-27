package org.apache.spark.abac.engine;

/**
 * Result of evaluating a condition.
 */
public class ConditionEvaluationResult {
    private final boolean satisfied;
    private final String reason;
    private final Object actualValue;
    private final Object expectedValue;

    public ConditionEvaluationResult(boolean satisfied, String reason, Object actualValue, Object expectedValue) {
        this.satisfied = satisfied;
        this.reason = reason;
        this.actualValue = actualValue;
        this.expectedValue = expectedValue;
    }

    public boolean isSatisfied() {
        return satisfied;
    }

    public String getReason() {
        return reason;
    }

    public Object getActualValue() {
        return actualValue;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    @Override
    public String toString() {
        return "ConditionEvaluationResult{" +
                "satisfied=" + satisfied +
                ", reason='" + reason + '\'' +
                '}';
    }
} 