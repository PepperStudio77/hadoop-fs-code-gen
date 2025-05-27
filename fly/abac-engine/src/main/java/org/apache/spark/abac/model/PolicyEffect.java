package org.apache.spark.abac.model;

/**
 * Defines the effect of a policy decision.
 */
public enum PolicyEffect {
    PERMIT,
    DENY,
    NOT_APPLICABLE
} 