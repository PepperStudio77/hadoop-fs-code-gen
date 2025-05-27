package org.apache.spark.abac.engine;

import org.apache.spark.abac.model.Policy;
import java.util.List;
import java.util.Optional;

/**
 * Interface for storing and retrieving ABAC policies.
 */
public interface PolicyStore {
    
    /**
     * Retrieves all policies.
     */
    List<Policy> getAllPolicies();
    
    /**
     * Retrieves a policy by ID.
     */
    Optional<Policy> getPolicyById(String policyId);
    
    /**
     * Stores a new policy.
     */
    void storePolicy(Policy policy);
    
    /**
     * Updates an existing policy.
     */
    void updatePolicy(Policy policy);
    
    /**
     * Deletes a policy by ID.
     */
    void deletePolicy(String policyId);
    
    /**
     * Refreshes the policy store from the underlying storage.
     */
    void refresh();
} 