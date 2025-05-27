package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.PolicyEffect;
import java.util.List;

/**
 * Result of evaluating a rule.
 */
public class RuleEvaluationResult {
    private final String ruleId;
    private final PolicyEffect effect;
    private final String reason;
    private final List<ConditionEvaluationResult> conditionResults;

    public RuleEvaluationResult(String ruleId, PolicyEffect effect, String reason, List<ConditionEvaluationResult> conditionResults) {
        this.ruleId = ruleId;
        this.effect = effect;
        this.reason = reason;
        this.conditionResults = conditionResults;
    }

    public String getRuleId() {
        return ruleId;
    }

    public PolicyEffect getEffect() {
        return effect;
    }

    public String getReason() {
        return reason;
    }

    public List<ConditionEvaluationResult> getConditionResults() {
        return conditionResults;
    }

    @Override
    public String toString() {
        return "RuleEvaluationResult{" +
                "ruleId='" + ruleId + '\'' +
                ", effect=" + effect +
                ", reason='" + reason + '\'' +
                '}';
    }
} 