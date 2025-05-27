package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.PolicyEffect;
import java.util.List;

/**
 * Represents the result of an authorization decision.
 */
public class AuthorizationDecision {
    private final PolicyEffect effect;
    private final String reason;
    private final List<PolicyEvaluationResult> policyResults;

    public AuthorizationDecision(PolicyEffect effect, String reason, List<PolicyEvaluationResult> policyResults) {
        this.effect = effect;
        this.reason = reason;
        this.policyResults = policyResults;
    }

    public PolicyEffect getEffect() {
        return effect;
    }

    public String getReason() {
        return reason;
    }

    public List<PolicyEvaluationResult> getPolicyResults() {
        return policyResults;
    }

    public boolean isPermitted() {
        return effect == PolicyEffect.PERMIT;
    }

    @Override
    public String toString() {
        return "AuthorizationDecision{" +
                "effect=" + effect +
                ", reason='" + reason + '\'' +
                '}';
    }
} 