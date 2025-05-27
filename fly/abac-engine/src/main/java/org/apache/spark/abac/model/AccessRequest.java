package org.apache.spark.abac.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an access request in the ABAC system.
 */
public class AccessRequest {
    private final String subject;
    private final String resource;
    private final String action;
    private final Map<String, Object> environment;
    private final String sessionId;

    public AccessRequest(String subject, String resource, String action, Map<String, Object> environment, String sessionId) {
        this.subject = Objects.requireNonNull(subject, "Subject cannot be null");
        this.resource = Objects.requireNonNull(resource, "Resource cannot be null");
        this.action = Objects.requireNonNull(action, "Action cannot be null");
        this.environment = environment;
        this.sessionId = sessionId;
    }

    public String getSubject() {
        return subject;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessRequest that = (AccessRequest) o;
        return Objects.equals(subject, that.subject) &&
                Objects.equals(resource, that.resource) &&
                Objects.equals(action, that.action) &&
                Objects.equals(environment, that.environment) &&
                Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, resource, action, environment, sessionId);
    }

    @Override
    public String toString() {
        return "AccessRequest{" +
                "subject='" + subject + '\'' +
                ", resource='" + resource + '\'' +
                ", action='" + action + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
} 