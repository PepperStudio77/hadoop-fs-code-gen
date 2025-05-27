package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.Condition;
import java.util.Map;

/**
 * Interface for evaluating conditions within rules.
 */
public interface ConditionEvaluator {
    
    /**
     * Evaluates a condition against the provided attributes.
     */
    ConditionEvaluationResult evaluate(Condition condition, Map<String, Object> attributes);
} 