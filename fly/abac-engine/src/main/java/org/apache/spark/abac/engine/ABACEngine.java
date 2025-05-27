package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core ABAC policy evaluation engine.
 * Evaluates access requests against configured policies.
 */
public class ABACEngine {
    private static final Logger logger = LoggerFactory.getLogger(ABACEngine.class);
    
    private final PolicyStore policyStore;
    private final AttributeResolver attributeResolver;
    private final ConditionEvaluator conditionEvaluator;
    private final DecisionCache decisionCache;

    public ABACEngine(PolicyStore policyStore, 
                     AttributeResolver attributeResolver,
                     ConditionEvaluator conditionEvaluator,
                     DecisionCache decisionCache) {
        this.policyStore = Objects.requireNonNull(policyStore, "PolicyStore cannot be null");
        this.attributeResolver = Objects.requireNonNull(attributeResolver, "AttributeResolver cannot be null");
        this.conditionEvaluator = Objects.requireNonNull(conditionEvaluator, "ConditionEvaluator cannot be null");
        this.decisionCache = decisionCache;
    }

    /**
     * Evaluates an access request and returns the authorization decision.
     */
    public AuthorizationDecision evaluate(AccessRequest request) {
        Objects.requireNonNull(request, "Access request cannot be null");
        
        logger.debug("Evaluating access request: {}", request);
        
        // Check cache first
        if (decisionCache != null) {
            AuthorizationDecision cached = decisionCache.get(request);
            if (cached != null) {
                logger.debug("Returning cached decision for request: {}", request);
                return cached;
            }
        }

        try {
            // Resolve attributes
            Map<String, Object> attributes = attributeResolver.resolve(request);
            
            // Get applicable policies
            List<Policy> applicablePolicies = getApplicablePolicies(request, attributes);
            
            if (applicablePolicies.isEmpty()) {
                logger.debug("No applicable policies found for request: {}", request);
                return new AuthorizationDecision(PolicyEffect.NOT_APPLICABLE, "No applicable policies", Collections.emptyList());
            }

            // Evaluate policies in priority order
            List<PolicyEvaluationResult> results = new ArrayList<>();
            PolicyEffect finalDecision = PolicyEffect.NOT_APPLICABLE;
            String reason = "No rules matched";

            for (Policy policy : applicablePolicies) {
                PolicyEvaluationResult result = evaluatePolicy(policy, request, attributes);
                results.add(result);
                
                if (result.getEffect() == PolicyEffect.PERMIT) {
                    finalDecision = PolicyEffect.PERMIT;
                    reason = "Access granted by policy: " + policy.getName();
                    break; // First permit wins
                } else if (result.getEffect() == PolicyEffect.DENY) {
                    finalDecision = PolicyEffect.DENY;
                    reason = "Access denied by policy: " + policy.getName();
                    // Continue to check for permits (permit overrides)
                }
            }

            AuthorizationDecision decision = new AuthorizationDecision(finalDecision, reason, results);
            
            // Cache the decision
            if (decisionCache != null) {
                decisionCache.put(request, decision);
            }
            
            logger.debug("Authorization decision for request {}: {}", request, decision);
            return decision;
            
        } catch (Exception e) {
            logger.error("Error evaluating access request: " + request, e);
            return new AuthorizationDecision(PolicyEffect.DENY, "Evaluation error: " + e.getMessage(), Collections.emptyList());
        }
    }

    /**
     * Gets policies that are applicable to the given request.
     */
    private List<Policy> getApplicablePolicies(AccessRequest request, Map<String, Object> attributes) {
        return policyStore.getAllPolicies().stream()
                .filter(Policy::isEnabled)
                .filter(policy -> isPolicyApplicable(policy, request, attributes))
                .sorted(Comparator.comparingInt(Policy::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Checks if a policy is applicable to the given request.
     */
    private boolean isPolicyApplicable(Policy policy, AccessRequest request, Map<String, Object> attributes) {
        PolicyTarget target = policy.getTarget();
        if (target == null) {
            return true; // No target means applies to all
        }

        // Check resource match
        if (target.getResources() != null && !target.getResources().isEmpty()) {
            boolean resourceMatch = target.getResources().stream()
                    .anyMatch(pattern -> matchesPattern(request.getResource(), pattern));
            if (!resourceMatch) {
                return false;
            }
        }

        // Check action match
        if (target.getActions() != null && !target.getActions().isEmpty()) {
            boolean actionMatch = target.getActions().stream()
                    .anyMatch(pattern -> matchesPattern(request.getAction(), pattern));
            if (!actionMatch) {
                return false;
            }
        }

        // Check subject match
        if (target.getSubjects() != null && !target.getSubjects().isEmpty()) {
            boolean subjectMatch = target.getSubjects().stream()
                    .anyMatch(pattern -> matchesPattern(request.getSubject(), pattern));
            if (!subjectMatch) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluates a single policy against the request.
     */
    private PolicyEvaluationResult evaluatePolicy(Policy policy, AccessRequest request, Map<String, Object> attributes) {
        logger.debug("Evaluating policy: {}", policy.getName());
        
        List<RuleEvaluationResult> ruleResults = new ArrayList<>();
        PolicyEffect policyEffect = policy.getDefaultEffect();
        String reason = "Default policy effect";

        for (Rule rule : policy.getRules()) {
            if (!rule.isEnabled()) {
                continue;
            }

            RuleEvaluationResult ruleResult = evaluateRule(rule, request, attributes);
            ruleResults.add(ruleResult);

            if (ruleResult.getEffect() == PolicyEffect.PERMIT) {
                policyEffect = PolicyEffect.PERMIT;
                reason = "Rule " + rule.getName() + " granted access";
                break; // First permit wins within policy
            } else if (ruleResult.getEffect() == PolicyEffect.DENY) {
                policyEffect = PolicyEffect.DENY;
                reason = "Rule " + rule.getName() + " denied access";
                // Continue to check for permits
            }
        }

        return new PolicyEvaluationResult(policy.getId(), policyEffect, reason, ruleResults);
    }

    /**
     * Evaluates a single rule against the request.
     */
    private RuleEvaluationResult evaluateRule(Rule rule, AccessRequest request, Map<String, Object> attributes) {
        logger.debug("Evaluating rule: {}", rule.getName());
        
        // Check rule target
        if (!isRuleApplicable(rule, request)) {
            return new RuleEvaluationResult(rule.getId(), PolicyEffect.NOT_APPLICABLE, "Rule target not matched", Collections.emptyList());
        }

        // Evaluate conditions
        List<ConditionEvaluationResult> conditionResults = new ArrayList<>();
        boolean allConditionsMet = true;

        if (rule.getConditions() != null) {
            for (Condition condition : rule.getConditions()) {
                ConditionEvaluationResult conditionResult = conditionEvaluator.evaluate(condition, attributes);
                conditionResults.add(conditionResult);
                
                if (!conditionResult.isSatisfied()) {
                    allConditionsMet = false;
                    break;
                }
            }
        }

        if (allConditionsMet) {
            return new RuleEvaluationResult(rule.getId(), rule.getEffect(), "All conditions satisfied", conditionResults);
        } else {
            return new RuleEvaluationResult(rule.getId(), PolicyEffect.NOT_APPLICABLE, "Conditions not satisfied", conditionResults);
        }
    }

    /**
     * Checks if a rule is applicable to the given request.
     */
    private boolean isRuleApplicable(Rule rule, AccessRequest request) {
        RuleTarget target = rule.getTarget();
        if (target == null) {
            return true;
        }

        // Similar logic to policy target matching
        if (target.getResources() != null && !target.getResources().isEmpty()) {
            boolean resourceMatch = target.getResources().stream()
                    .anyMatch(pattern -> matchesPattern(request.getResource(), pattern));
            if (!resourceMatch) {
                return false;
            }
        }

        if (target.getActions() != null && !target.getActions().isEmpty()) {
            boolean actionMatch = target.getActions().stream()
                    .anyMatch(pattern -> matchesPattern(request.getAction(), pattern));
            if (!actionMatch) {
                return false;
            }
        }

        if (target.getSubjects() != null && !target.getSubjects().isEmpty()) {
            boolean subjectMatch = target.getSubjects().stream()
                    .anyMatch(pattern -> matchesPattern(request.getSubject(), pattern));
            if (!subjectMatch) {
                return false;
            }
        }

        return true;
    }

    /**
     * Simple pattern matching with wildcard support.
     */
    private boolean matchesPattern(String value, String pattern) {
        if (pattern.equals("*")) {
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern.replace("*", ".*");
            return value.matches(regex);
        }
        return value.equals(pattern);
    }
} 