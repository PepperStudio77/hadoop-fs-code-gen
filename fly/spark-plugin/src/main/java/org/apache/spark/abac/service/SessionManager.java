package org.apache.spark.abac.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user sessions and their associated permissions.
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates or updates a user session.
     */
    public void createSession(String sessionId, String userId, Map<String, Object> userAttributes) {
        UserSession session = new UserSession(sessionId, userId, userAttributes);
        sessions.put(sessionId, session);
        logger.info("Created session {} for user {}", sessionId, userId);
    }

    /**
     * Gets a user session by session ID.
     */
    public UserSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Removes a user session.
     */
    public void removeSession(String sessionId) {
        UserSession removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.info("Removed session {} for user {}", sessionId, removed.getUserId());
        }
    }

    /**
     * Gets the current user ID for a session.
     */
    public String getCurrentUserId(String sessionId) {
        UserSession session = sessions.get(sessionId);
        return session != null ? session.getUserId() : null;
    }

    /**
     * Represents a user session with associated attributes.
     */
    public static class UserSession {
        private final String sessionId;
        private final String userId;
        private final Map<String, Object> userAttributes;
        private final long createdAt;

        public UserSession(String sessionId, String userId, Map<String, Object> userAttributes) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.userAttributes = userAttributes;
            this.createdAt = System.currentTimeMillis();
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
        }

        public Map<String, Object> getUserAttributes() {
            return userAttributes;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
} 