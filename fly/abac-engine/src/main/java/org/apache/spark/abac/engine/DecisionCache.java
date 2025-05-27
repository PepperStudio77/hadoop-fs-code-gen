package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.AccessRequest;

/**
 * Interface for caching authorization decisions.
 */
public interface DecisionCache {
    
    /**
     * Gets a cached authorization decision for the given request.
     */
    AuthorizationDecision get(AccessRequest request);
    
    /**
     * Caches an authorization decision for the given request.
     */
    void put(AccessRequest request, AuthorizationDecision decision);
    
    /**
     * Invalidates the cache for the given request.
     */
    void invalidate(AccessRequest request);
    
    /**
     * Clears all cached decisions.
     */
    void clear();
} 