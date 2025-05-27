package org.apache.spark.abac.impl;

import org.apache.spark.abac.engine.PolicyStore;
import org.apache.spark.abac.model.Policy;
import org.apache.spark.abac.service.PolicyServiceClient;

import java.util.List;
import java.util.Optional;

/**
 * Policy store implementation that retrieves policies from a remote service.
 */
public class RemotePolicyStore implements PolicyStore {
    private final PolicyServiceClient policyServiceClient;

    public RemotePolicyStore(PolicyServiceClient policyServiceClient) {
        this.policyServiceClient = policyServiceClient;
    }

    @Override
    public List<Policy> getAllPolicies() {
        return policyServiceClient.getAllPolicies();
    }

    @Override
    public Optional<Policy> getPolicyById(String policyId) {
        Policy policy = policyServiceClient.getPolicyById(policyId);
        return Optional.ofNullable(policy);
    }

    @Override
    public void storePolicy(Policy policy) {
        throw new UnsupportedOperationException("Policy storage not supported in read-only remote store");
    }

    @Override
    public void updatePolicy(Policy policy) {
        throw new UnsupportedOperationException("Policy updates not supported in read-only remote store");
    }

    @Override
    public void deletePolicy(String policyId) {
        throw new UnsupportedOperationException("Policy deletion not supported in read-only remote store");
    }

    @Override
    public void refresh() {
        // No-op for remote store, policies are fetched on-demand
    }
} 