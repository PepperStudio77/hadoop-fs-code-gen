package com.spark.hdfs.sas.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Map;

/**
 * Response model for SAS URL generation
 */
public class SASResponse {
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("expires_at")
    private Date expiresAt;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    public SASResponse() {
    }
    
    public SASResponse(String url, Date expiresAt, Map<String, String> metadata) {
        this.url = url;
        this.expiresAt = expiresAt;
        this.metadata = metadata;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Date getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(Date expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Check if the SAS URL has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }
    
    @Override
    public String toString() {
        return "SASResponse{" +
                "url='" + url + '\'' +
                ", expiresAt=" + expiresAt +
                ", metadata=" + metadata +
                '}';
    }
} 