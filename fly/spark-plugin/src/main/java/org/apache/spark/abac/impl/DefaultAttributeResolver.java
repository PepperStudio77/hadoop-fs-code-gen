package org.apache.spark.abac.impl;

import org.apache.spark.abac.engine.AttributeResolver;
import org.apache.spark.abac.model.AccessRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of AttributeResolver that extracts attributes from the access request
 * and resolves additional attributes from external sources.
 */
public class DefaultAttributeResolver implements AttributeResolver {
    private static final Logger logger = LoggerFactory.getLogger(DefaultAttributeResolver.class);

    @Override
    public Map<String, Object> resolve(AccessRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        
        // Add basic request attributes
        attributes.put("subject", request.getSubject());
        attributes.put("resource", request.getResource());
        attributes.put("action", request.getAction());
        attributes.put("sessionId", request.getSessionId());
        
        // Add environment attributes
        if (request.getEnvironment() != null) {
            attributes.putAll(request.getEnvironment());
        }
        
        // Add time-based attributes
        attributes.put("currentTime", System.currentTimeMillis());
        attributes.put("currentHour", java.time.LocalTime.now().getHour());
        attributes.put("currentDay", java.time.LocalDate.now().getDayOfWeek().toString());
        
        // Resolve user attributes (in a real implementation, this would query user directory)
        resolveUserAttributes(request.getSubject(), attributes);
        
        // Resolve resource attributes
        resolveResourceAttributes(request.getResource(), attributes);
        
        logger.debug("Resolved attributes for request {}: {}", request, attributes);
        return attributes;
    }
    
    private void resolveUserAttributes(String subject, Map<String, Object> attributes) {
        // In a real implementation, this would query LDAP or user directory
        // For now, we'll extract from subject or use defaults
        
        if (subject.contains("@")) {
            String[] parts = subject.split("@");
            attributes.put("user.username", parts[0]);
            attributes.put("user.domain", parts[1]);
        } else {
            attributes.put("user.username", subject);
        }
        
        // Mock user attributes based on username patterns
        String username = (String) attributes.get("user.username");
        if (username.startsWith("hr_")) {
            attributes.put("user.department", "HR");
            attributes.put("user.clearanceLevel", 2);
        } else if (username.startsWith("finance_")) {
            attributes.put("user.department", "Finance");
            attributes.put("user.clearanceLevel", 3);
        } else if (username.startsWith("manager_")) {
            attributes.put("user.role", "Manager");
            attributes.put("user.clearanceLevel", 4);
        } else {
            attributes.put("user.department", "General");
            attributes.put("user.clearanceLevel", 1);
        }
    }
    
    private void resolveResourceAttributes(String resource, Map<String, Object> attributes) {
        // Extract resource metadata
        attributes.put("resource.path", resource);
        
        if (resource.startsWith("s3://")) {
            attributes.put("resource.type", "s3");
            String[] parts = resource.replace("s3://", "").split("/");
            if (parts.length > 0) {
                attributes.put("resource.bucket", parts[0]);
            }
            if (parts.length > 1) {
                attributes.put("resource.prefix", String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length)));
            }
        }
        
        // Determine data sensitivity level based on path
        if (resource.contains("/sensitive/") || resource.contains("/pii/")) {
            attributes.put("resource.sensitivityLevel", 5);
        } else if (resource.contains("/confidential/")) {
            attributes.put("resource.sensitivityLevel", 4);
        } else if (resource.contains("/internal/")) {
            attributes.put("resource.sensitivityLevel", 3);
        } else {
            attributes.put("resource.sensitivityLevel", 1);
        }
    }
} 