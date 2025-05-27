package org.apache.spark.abac;

import org.apache.spark.abac.engine.*;
import org.apache.spark.abac.service.PolicyServiceClient;
import org.apache.spark.abac.impl.*;

/**
 * Factory for creating ABAC engine instances.
 */
public class ABACEngineFactory {
    
    public static ABACEngine create(PolicyServiceClient policyServiceClient) {
        // Create policy store backed by policy service
        PolicyStore policyStore = new RemotePolicyStore(policyServiceClient);
        
        // Create attribute resolver
        AttributeResolver attributeResolver = new DefaultAttributeResolver();
        
        // Create condition evaluator
        ConditionEvaluator conditionEvaluator = new DefaultConditionEvaluator();
        
        // Create decision cache
        DecisionCache decisionCache = new CaffeineDecisionCache();
        
        return new ABACEngine(policyStore, attributeResolver, conditionEvaluator, decisionCache);
    }
} 