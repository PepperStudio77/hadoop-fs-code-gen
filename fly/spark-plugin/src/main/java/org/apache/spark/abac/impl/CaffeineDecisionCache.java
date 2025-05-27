package org.apache.spark.abac.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.spark.abac.engine.AuthorizationDecision;
import org.apache.spark.abac.engine.DecisionCache;
import org.apache.spark.abac.model.AccessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Caffeine-based implementation of DecisionCache for high-performance caching.
 */
public class CaffeineDecisionCache implements DecisionCache {
    private static final Logger logger = LoggerFactory.getLogger(CaffeineDecisionCache.class);
    
    private final Cache<String, AuthorizationDecision> cache;

    public CaffeineDecisionCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats()
                .build();
        
        logger.info("Initialized Caffeine decision cache with 10K max size and 5-minute TTL");
    }

    public CaffeineDecisionCache(long maxSize, Duration ttl) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
        
        logger.info("Initialized Caffeine decision cache with {} max size and {} TTL", maxSize, ttl);
    }

    @Override
    public AuthorizationDecision get(AccessRequest request) {
        String key = createCacheKey(request);
        AuthorizationDecision decision = cache.getIfPresent(key);
        
        if (decision != null) {
            logger.debug("Cache hit for request: {}", request);
        } else {
            logger.debug("Cache miss for request: {}", request);
        }
        
        return decision;
    }

    @Override
    public void put(AccessRequest request, AuthorizationDecision decision) {
        String key = createCacheKey(request);
        cache.put(key, decision);
        logger.debug("Cached decision for request: {}", request);
    }

    @Override
    public void invalidate(AccessRequest request) {
        String key = createCacheKey(request);
        cache.invalidate(key);
        logger.debug("Invalidated cache for request: {}", request);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        logger.info("Cleared all cached decisions");
    }

    /**
     * Creates a cache key from an access request.
     */
    private String createCacheKey(AccessRequest request) {
        return String.format("%s|%s|%s|%s", 
            request.getSubject(), 
            request.getResource(), 
            request.getAction(),
            request.getSessionId());
    }

    /**
     * Gets cache statistics for monitoring.
     */
    public String getCacheStats() {
        return cache.stats().toString();
    }
} 