package org.apache.spark.abac.enforcement;

import org.apache.spark.abac.engine.ABACEngine;
import org.apache.spark.abac.service.SessionManager;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.logical.LeafNode;
import org.apache.spark.sql.execution.datasources.LogicalRelation;
import org.apache.spark.sql.execution.datasources.HadoopFsRelation;
import org.apache.spark.sql.catalyst.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Iterator;

/**
 * Catalyst rule that intercepts logical plans and enforces ABAC policies.
 * This is how we actually hook into Spark's query execution to call ABACEngine.evaluate().
 */
public class SparkCatalystInterceptor extends Rule<LogicalPlan> {
    private static final Logger logger = LoggerFactory.getLogger(SparkCatalystInterceptor.class);
    
    private final ABACEngine abacEngine;
    private final SessionManager sessionManager;
    private final ABACDataSourceInterceptor dataSourceInterceptor;

    public SparkCatalystInterceptor(ABACEngine abacEngine, SessionManager sessionManager) {
        this.abacEngine = abacEngine;
        this.sessionManager = sessionManager;
        this.dataSourceInterceptor = new ABACDataSourceInterceptor(abacEngine, sessionManager);
    }

    @Override
    public String ruleName() {
        return "ABACAccessControlRule";
    }

    /**
     * This method is called by Spark's Catalyst optimizer for every logical plan.
     * Here we intercept data source access and call the ABAC engine.
     */
    @Override
    public LogicalPlan apply(LogicalPlan plan) {
        try {
            // Traverse the logical plan to find data source operations
            checkDataSourceAccess(plan);
            return plan; // Return the plan unchanged if access is granted
        } catch (SecurityException e) {
            // Re-throw security exceptions to prevent query execution
            logger.error("ABAC access control denied query execution: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error during ABAC access control check", e);
            throw new SecurityException("Access control check failed: " + e.getMessage());
        }
    }

    /**
     * Recursively checks data source access in the logical plan.
     */
    private void checkDataSourceAccess(LogicalPlan plan) {
        // Check if this is a data source operation
        if (plan instanceof LogicalRelation) {
            LogicalRelation relation = (LogicalRelation) plan;
            checkLogicalRelationAccess(relation);
        }

        // Recursively check children
        Iterator<LogicalPlan> children = plan.children().iterator();
        while (children.hasNext()) {
            checkDataSourceAccess(children.next());
        }
    }

    /**
     * Checks access for LogicalRelation nodes (data source reads).
     */
    private void checkLogicalRelationAccess(LogicalRelation relation) {
        try {
            // Extract file paths from the relation
            if (relation.relation() instanceof HadoopFsRelation) {
                HadoopFsRelation fsRelation = (HadoopFsRelation) relation.relation();
                
                // Get file paths
                Iterator<String> pathIterator = fsRelation.location().rootPaths().iterator();
                while (pathIterator.hasNext()) {
                    String path = pathIterator.next();
                    
                    // Get current Spark session
                    SparkSession spark = SparkSession.active();
                    
                    // Determine data format
                    String format = determineDataFormat(fsRelation);
                    
                    logger.info("Intercepted data access to: {} (format: {})", path, format);
                    
                    // THIS IS WHERE ABACEngine.evaluate() GETS CALLED!
                    dataSourceInterceptor.checkReadAccess(spark, path, format);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking access for LogicalRelation: {}", relation, e);
            throw new SecurityException("Failed to verify access permissions: " + e.getMessage());
        }
    }

    /**
     * Determines the data format from the relation.
     */
    private String determineDataFormat(HadoopFsRelation relation) {
        String className = relation.fileFormat().getClass().getSimpleName();
        
        if (className.contains("Parquet")) {
            return "parquet";
        } else if (className.contains("Delta")) {
            return "delta";
        } else if (className.contains("Json")) {
            return "json";
        } else if (className.contains("Csv")) {
            return "csv";
        } else {
            return "unknown";
        }
    }
} 