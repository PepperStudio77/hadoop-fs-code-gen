package org.apache.spark.abac.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.spark.abac.model.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Client for communicating with the policy management service.
 */
public class PolicyServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(PolicyServiceClient.class);
    
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PolicyServiceClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Retrieves all policies from the policy service.
     */
    public List<Policy> getAllPolicies() {
        try {
            HttpGet request = new HttpGet(baseUrl + "/api/policies");
            request.setHeader("Accept", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String jsonResponse = EntityUtils.toString(response.getEntity());
                Policy[] policies = objectMapper.readValue(jsonResponse, Policy[].class);
                return Arrays.asList(policies);
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve policies from service", e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves a specific policy by ID.
     */
    public Policy getPolicyById(String policyId) {
        try {
            HttpGet request = new HttpGet(baseUrl + "/api/policies/" + policyId);
            request.setHeader("Accept", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                if (response.getStatusLine().getStatusCode() == 404) {
                    return null;
                }
                String jsonResponse = EntityUtils.toString(response.getEntity());
                return objectMapper.readValue(jsonResponse, Policy.class);
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve policy {} from service", policyId, e);
            return null;
        }
    }

    /**
     * Closes the HTTP client.
     */
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
} 