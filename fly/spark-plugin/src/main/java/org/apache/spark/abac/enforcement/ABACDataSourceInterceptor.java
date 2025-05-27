package org.apache.spark.abac.enforcement;

import org.apache.spark.abac.engine.ABACEngine;
import org.apache.spark.abac.engine.AuthorizationDecision;
import org.apache.spark.abac.model.AccessRequest;
import org.apache.spark.abac.model.PolicyEffect;
import org.apache.spark.abac.service.SessionManager;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor that enforces ABAC policies for data source access in Spark.
 * This is where access denial actually happens when users lack proper permissions.
 */
public class ABACDataSourceInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ABACDataSourceInterceptor.class);
    
    private final ABACEngine abacEngine;
    private final SessionManager sessionManager;

    public ABACDataSourceInterceptor(ABACEngine abacEngine, SessionManager sessionManager) {
        this.abacEngine = abacEngine;
        this.sessionManager = sessionManager;
    }

    /**
     * Intercepts data source read operations and enforces ABAC policies.
     * This method is called before Spark actually reads data from the source.
     * 
     * @param sparkSession The current Spark session
     * @param resourcePath The path to the data resource being accessed
     * @param format The data format (parquet, delta, etc.)
     * @throws SecurityException if access is denied
     */
    public void checkReadAccess(SparkSession sparkSession, String resourcePath, String format) {
        String sessionId = sparkSession.conf().get("spark.abac.session.id", "unknown");
        String userId = sparkSession.conf().get("spark.abac.user.id", "unknown");
        
        logger.info("Checking read access for user {} in session {} to resource: {}", 
                   userId, sessionId, resourcePath);

        // Create access request
        Map<String, Object> environment = new HashMap<>();
        environment.put("spark.app.name", sparkSession.conf().get("spark.app.name", "unknown"));
        environment.put("spark.session.id", sessionId);
        environment.put("data.format", format);
        
        AccessRequest request = new AccessRequest(
            userId,           // subject
            resourcePath,     // resource  
            "read",          // action
            environment,     // environment
            sessionId        // sessionId
        );

        // Evaluate ABAC policy
        AuthorizationDecision decision = abacEngine.evaluate(request);
        
        // Log the decision for audit purposes
        logger.info("Access decision for user {} to resource {}: {} - {}", 
                   userId, resourcePath, decision.getEffect(), decision.getReason());

        // ENFORCE THE DECISION - This is where access denial happens
        if (!decision.isPermitted()) {
            String errorMessage = String.format(
                "Access denied to resource '%s' for user '%s' in session '%s'. Reason: %s", 
                resourcePath, userId, sessionId, decision.getReason()
            );
            
            logger.warn("ACCESS DENIED: {}", errorMessage);
            
            // Throw SecurityException to prevent Spark from accessing the data
            throw new SecurityException(errorMessage);
        }
        
        logger.info("ACCESS GRANTED: User {} can read resource {}", userId, resourcePath);
    }

    /**
     * Intercepts data source write operations and enforces ABAC policies.
     */
    public void checkWriteAccess(SparkSession sparkSession, String resourcePath, String format) {
        String sessionId = sparkSession.conf().get("spark.abac.session.id", "unknown");
        String userId = sparkSession.conf().get("spark.abac.user.id", "unknown");
        
        logger.info("Checking write access for user {} in session {} to resource: {}", 
                   userId, sessionId, resourcePath);

        Map<String, Object> environment = new HashMap<>();
        environment.put("spark.app.name", sparkSession.conf().get("spark.app.name", "unknown"));
        environment.put("spark.session.id", sessionId);
        environment.put("data.format", format);
        
        AccessRequest request = new AccessRequest(
            userId,           // subject
            resourcePath,     // resource  
            "write",         // action
            environment,     // environment
            sessionId        // sessionId
        );

        AuthorizationDecision decision = abacEngine.evaluate(request);
        
        logger.info("Write access decision for user {} to resource {}: {} - {}", 
                   userId, resourcePath, decision.getEffect(), decision.getReason());

        if (!decision.isPermitted()) {
            String errorMessage = String.format(
                "Write access denied to resource '%s' for user '%s' in session '%s'. Reason: %s", 
                resourcePath, userId, sessionId, decision.getReason()
            );
            
            logger.warn("WRITE ACCESS DENIED: {}", errorMessage);
            throw new SecurityException(errorMessage);
        }
        
        logger.info("WRITE ACCESS GRANTED: User {} can write to resource {}", userId, resourcePath);
    }

    /**
     * Handles different types of access denial scenarios.
     */
    private void handleAccessDenial(AccessRequest request, AuthorizationDecision decision) {
        String denialType;
        
        switch (decision.getEffect()) {
            case DENY:
                denialType = "EXPLICIT_DENY";
                break;
            case NOT_APPLICABLE:
                denialType = "NO_APPLICABLE_POLICY";
                break;
            default:
                denialType = "UNKNOWN";
        }
        
        // Log detailed denial information for audit and debugging
        logger.warn("Access denied - Type: {}, User: {}, Resource: {}, Action: {}, Reason: {}", 
                   denialType, request.getSubject(), request.getResource(), 
                   request.getAction(), decision.getReason());
        
        // Could also trigger alerts, notifications, or additional security measures here
        if (denialType.equals("NO_APPLICABLE_POLICY")) {
            logger.warn("SECURITY ALERT: User {} attempted to access resource {} but no policies were found. " +
                       "This could indicate a misconfiguration or unauthorized access attempt.", 
                       request.getSubject(), request.getResource());
        }
    }
} 