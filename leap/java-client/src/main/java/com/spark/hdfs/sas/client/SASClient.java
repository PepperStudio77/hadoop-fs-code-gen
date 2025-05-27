package com.spark.hdfs.sas.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with the SAS backend service
 */
public class SASClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(SASClient.class);
    
    private final String serviceUrl;
    private final String authToken;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public SASClient(String serviceUrl, String authToken) {
        this.serviceUrl = serviceUrl.endsWith("/") ? serviceUrl : serviceUrl + "/";
        this.authToken = authToken;
        this.objectMapper = new ObjectMapper();
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }
    
    /**
     * Request a SAS URL for the given path and operation
     */
    public SASResponse getSASURL(String path, String operation) throws IOException {
        return getSASURL(path, operation, 1, null);
    }
    
    /**
     * Request a SAS URL with custom expiry and metadata
     */
    public SASResponse getSASURL(String path, String operation, int expiryHours, Map<String, String> metadata) throws IOException {
        LOG.debug("Requesting SAS URL for path: {}, operation: {}", path, operation);
        
        // Prepare request body
        SASRequest request = new SASRequest();
        request.setPath(path);
        request.setOperation(operation);
        request.setExpiryHours(expiryHours);
        request.setMetadata(metadata != null ? metadata : new HashMap<>());
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        RequestBody body = RequestBody.create(
            requestJson,
            MediaType.get("application/json; charset=utf-8")
        );
        
        Request httpRequest = new Request.Builder()
            .url(serviceUrl + "api/v1/sas")
            .post(body)
            .addHeader("Authorization", "Bearer " + authToken)
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("SAS request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            SASResponse sasResponse = objectMapper.readValue(responseBody, SASResponse.class);
            
            LOG.debug("Received SAS URL for path: {}, expires at: {}", path, sasResponse.getExpiresAt());
            return sasResponse;
        }
    }
    
    /**
     * Check service health
     */
    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                .url(serviceUrl + "health")
                .get()
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            LOG.warn("Health check failed", e);
            return false;
        }
    }
    
    /**
     * Get list of policies from the service
     */
    public String[] getPolicies() throws IOException {
        Request request = new Request.Builder()
            .url(serviceUrl + "api/v1/policies")
            .get()
            .addHeader("Authorization", "Bearer " + authToken)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get policies: " + response.code());
            }
            
            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            
            if (result.containsKey("policies")) {
                return ((java.util.List<String>) result.get("policies")).toArray(new String[0]);
            }
            
            return new String[0];
        }
    }
    
    /**
     * Reload policies on the service
     */
    public void reloadPolicies() throws IOException {
        RequestBody body = RequestBody.create("", MediaType.get("application/json"));
        
        Request request = new Request.Builder()
            .url(serviceUrl + "api/v1/policies/reload")
            .post(body)
            .addHeader("Authorization", "Bearer " + authToken)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to reload policies: " + response.code());
            }
        }
    }
    
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
} 