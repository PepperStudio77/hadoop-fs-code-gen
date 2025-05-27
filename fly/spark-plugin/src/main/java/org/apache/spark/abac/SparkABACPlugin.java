package org.apache.spark.abac;

import org.apache.spark.SparkContext;
import org.apache.spark.api.plugin.DriverPlugin;
import org.apache.spark.api.plugin.ExecutorPlugin;
import org.apache.spark.api.plugin.PluginContext;
import org.apache.spark.api.plugin.SparkPlugin;
import org.apache.spark.abac.engine.ABACEngine;
import org.apache.spark.abac.service.PolicyServiceClient;
import org.apache.spark.abac.service.SessionManager;
import org.apache.spark.abac.enforcement.SparkCatalystInterceptor;
import org.apache.spark.internal.Logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

import java.util.Map;

/**
 * Main Spark ABAC Plugin that provides session-based access control for data lake resources.
 */
public class SparkABACPlugin implements SparkPlugin {
    private static final Logger logger = LoggerFactory.getLogger(SparkABACPlugin.class);

    @Override
    public DriverPlugin driverPlugin() {
        return new ABACDriverPlugin();
    }

    @Override
    public ExecutorPlugin executorPlugin() {
        return new ABACExecutorPlugin();
    }

    /**
     * Driver-side plugin implementation.
     */
    private static class ABACDriverPlugin implements DriverPlugin {
        private ABACEngine abacEngine;
        private SessionManager sessionManager;
        private PolicyServiceClient policyServiceClient;

        @Override
        public Map<String, String> init(SparkContext sc, PluginContext pluginContext) {
            logger.info("Initializing Spark ABAC Plugin on driver");
            
            try {
                // Initialize policy service client
                String policyServiceUrl = sc.conf().get("spark.abac.policy.service.url", "http://localhost:8080");
                policyServiceClient = new PolicyServiceClient(policyServiceUrl);
                
                // Initialize session manager
                sessionManager = new SessionManager();
                
                // Initialize ABAC engine
                abacEngine = ABACEngineFactory.create(policyServiceClient);
                
                // Register SQL hooks for access control
                registerSQLHooks(sc);
                
                logger.info("Spark ABAC Plugin initialized successfully");
                return Map.of("abac.status", "initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize Spark ABAC Plugin", e);
                throw new RuntimeException("ABAC Plugin initialization failed", e);
            }
        }

        @Override
        public void shutdown() {
            logger.info("Shutting down Spark ABAC Plugin on driver");
            if (policyServiceClient != null) {
                policyServiceClient.close();
            }
        }

        private void registerSQLHooks(SparkContext sc) {
            // Register hooks for intercepting SQL queries and data access
            logger.info("Registering SQL hooks for ABAC enforcement");
            
            try {
                // Get the Spark session from the context
                SparkSession spark = SparkSession.builder().sparkContext(sc).getOrCreate();
                
                // Create the ABAC Catalyst interceptor
                SparkCatalystInterceptor abacInterceptor = new SparkCatalystInterceptor(abacEngine, sessionManager);
                
                // Add the interceptor to Spark's session state
                // This hooks into Catalyst's rule-based optimization engine
                spark.sessionState().analyzer().batches().foreach(batch -> {
                    // Add our ABAC rule to the resolution batch (before other optimizations)
                    if (batch.name().equals("Resolution")) {
                        batch.rules().append(abacInterceptor);
                        logger.info("ABAC interceptor added to Catalyst Resolution batch");
                    }
                    return null;
                });
                
                logger.info("Successfully registered ABAC Catalyst interceptor");
                
            } catch (Exception e) {
                logger.error("Failed to register SQL hooks for ABAC enforcement", e);
                throw new RuntimeException("Could not register ABAC SQL hooks", e);
            }
        }
    }

    /**
     * Executor-side plugin implementation.
     */
    private static class ABACExecutorPlugin implements ExecutorPlugin {
        private ABACEngine abacEngine;
        private PolicyServiceClient policyServiceClient;

        @Override
        public void init(PluginContext ctx, Map<String, String> extraConf) {
            logger.info("Initializing Spark ABAC Plugin on executor");
            
            try {
                // Initialize policy service client
                String policyServiceUrl = extraConf.getOrDefault("spark.abac.policy.service.url", "http://localhost:8080");
                policyServiceClient = new PolicyServiceClient(policyServiceUrl);
                
                // Initialize ABAC engine
                abacEngine = ABACEngineFactory.create(policyServiceClient);
                
                logger.info("Spark ABAC Plugin initialized on executor");
            } catch (Exception e) {
                logger.error("Failed to initialize Spark ABAC Plugin on executor", e);
                throw new RuntimeException("ABAC Plugin executor initialization failed", e);
            }
        }

        @Override
        public void shutdown() {
            logger.info("Shutting down Spark ABAC Plugin on executor");
            if (policyServiceClient != null) {
                policyServiceClient.close();
            }
        }
    }
} 