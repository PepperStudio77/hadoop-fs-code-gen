package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.PolicyEffect;
import java.util.List;

/**
 * Result of evaluating a policy.
 */
public class PolicyEvaluationResult {
    private final String policyId;
    private final PolicyEffect effect;
    private final String reason;
    private final List<RuleEvaluationResult> ruleResults;

    public PolicyEvaluationResult(String policyId, PolicyEffect effect, String reason, List<RuleEvaluationResult> ruleResults) {
        this.policyId = policyId;
        this.effect = effect;
        this.reason = reason;
        this.ruleResults = ruleResults;
    }

    public String getPolicyId() {
        return policyId;
    }

    public PolicyEffect getEffect() {
        return effect;
    }

    public String getReason() {
        return reason;
    }

    public List<RuleEvaluationResult> getRuleResults() {
        return ruleResults;
    }

    @Override
    public String toString() {
        return "PolicyEvaluationResult{" +
                "policyId='" + policyId + '\'' +
                ", effect=" + effect +
                ", reason='" + reason + '\'' +
                '}';
    }
} 