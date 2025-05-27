package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.AccessRequest;
import java.util.Map;

/**
 * Interface for resolving attributes from access requests.
 */
public interface AttributeResolver {
    
    /**
     * Resolves attributes for the given access request.
     * This includes subject attributes, resource attributes, and environment attributes.
     */
    Map<String, Object> resolve(AccessRequest request);
} 