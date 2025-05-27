package com.spark.hdfs.sas.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request model for SAS URL generation
 */
public class SASRequest {
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("operation")
    private String operation;
    
    @JsonProperty("expiry_hours")
    private int expiryHours;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    public SASRequest() {
    }
    
    public SASRequest(String path, String operation, int expiryHours, Map<String, String> metadata) {
        this.path = path;
        this.operation = operation;
        this.expiryHours = expiryHours;
        this.metadata = metadata;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public int getExpiryHours() {
        return expiryHours;
    }
    
    public void setExpiryHours(int expiryHours) {
        this.expiryHours = expiryHours;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    @Override
    public String toString() {
        return "SASRequest{" +
                "path='" + path + '\'' +
                ", operation='" + operation + '\'' +
                ", expiryHours=" + expiryHours +
                ", metadata=" + metadata +
                '}';
    }
} 