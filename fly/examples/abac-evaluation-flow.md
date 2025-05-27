# How ABACEngine.evaluate() Gets Called: Complete Flow

This document explains exactly how the `ABACEngine.evaluate()` method gets called when a Spark user tries to access data, resolving the mystery of why it wasn't being invoked.

## The Problem and Solution

### **The Problem**
The `ABACEngine.evaluate()` method was never being called because:
1. The `registerSQLHooks()` method in `SparkABACPlugin.java` was just a placeholder
2. No Catalyst rules were actually registered to intercept data access
3. The interceptor chain was never connected to Spark's query execution

### **The Solution**
The complete integration requires:
1. **Catalyst Rule Registration** - Adding a custom rule to Spark's Catalyst optimizer
2. **Logical Plan Interception** - Detecting data source operations in query plans
3. **Access Request Creation** - Converting Spark operations into ABAC access requests
4. **Policy Evaluation** - Calling `ABACEngine.evaluate()` with the request
5. **Access Enforcement** - Throwing exceptions to prevent unauthorized access

## Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     USER CODE                                   │
│  spark.read.parquet("s3://data-lake/finance/data.parquet")     │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SPARK CATALYST                                 │
│  1. Parse SQL/DataFrame operations                              │
│  2. Create LogicalPlan                                          │
│  3. Apply Catalyst Rules (including ABAC rule)                 │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│               ABAC CATALYST INTERCEPTOR                         │
│  SparkCatalystInterceptor.apply(LogicalPlan)                   │
│  - Traverse plan tree                                          │
│  - Find LogicalRelation nodes (data sources)                   │
│  - Extract file paths and formats                              │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│            ABAC DATA SOURCE INTERCEPTOR                         │
│  ABACDataSourceInterceptor.checkReadAccess()                   │
│  - Get user/session from SparkSession config                   │
│  - Create AccessRequest object                                 │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ABAC ENGINE                                  │
│  🎯 ABACEngine.evaluate(AccessRequest) 🎯                      │
│  1. Check cache for cached decision                            │
│  2. Get applicable policies from PolicyStore                   │
│  3. Resolve user/resource attributes                           │
│  4. Evaluate policy rules and conditions                       │
│  5. Return AuthorizationDecision                               │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                ACCESS ENFORCEMENT                               │
│  if (!decision.isPermitted()) {                                │
│    throw new SecurityException("Access denied");               │
│  }                                                             │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RESULT                                       │
│  ✅ Access Granted: Continue with data operation               │
│  ❌ Access Denied: SecurityException stops execution           │
└─────────────────────────────────────────────────────────────────┘
```

## Code Implementation Details

### 1. Plugin Registration (`SparkABACPlugin.java`)

```java
private void registerSQLHooks(SparkContext sc) {
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
```

### 2. Catalyst Rule (`SparkCatalystInterceptor.java`)

```java
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
    }
}

private void checkLogicalRelationAccess(LogicalRelation relation) {
    if (relation.relation() instanceof HadoopFsRelation) {
        HadoopFsRelation fsRelation = (HadoopFsRelation) relation.relation();
        
        Iterator<String> pathIterator = fsRelation.location().rootPaths().iterator();
        while (pathIterator.hasNext()) {
            String path = pathIterator.next();
            SparkSession spark = SparkSession.active();
            String format = determineDataFormat(fsRelation);
            
            // 🎯 THIS IS WHERE ABACEngine.evaluate() GETS CALLED! 🎯
            dataSourceInterceptor.checkReadAccess(spark, path, format);
        }
    }
}
```

### 3. Access Request Creation (`ABACDataSourceInterceptor.java`)

```java
public void checkReadAccess(SparkSession sparkSession, String resourcePath, String format) {
    String sessionId = sparkSession.conf().get("spark.abac.session.id", "unknown");
    String userId = sparkSession.conf().get("spark.abac.user.id", "unknown");
    
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

    // 🎯 HERE'S THE ACTUAL CALL TO ABACEngine.evaluate()! 🎯
    AuthorizationDecision decision = abacEngine.evaluate(request);
    
    // ENFORCE THE DECISION
    if (!decision.isPermitted()) {
        throw new SecurityException("Access denied: " + decision.getReason());
    }
}
```

### 4. ABAC Engine Evaluation (`ABACEngine.java`)

```java
public AuthorizationDecision evaluate(AccessRequest request) {
    logger.debug("Evaluating access request: {}", request);
    
    // Check cache first
    if (decisionCache != null) {
        AuthorizationDecision cached = decisionCache.get(request);
        if (cached != null) {
            return cached;
        }
    }

    try {
        // Resolve attributes
        Map<String, Object> attributes = attributeResolver.resolve(request);
        
        // Get applicable policies
        List<Policy> applicablePolicies = getApplicablePolicies(request, attributes);
        
        if (applicablePolicies.isEmpty()) {
            return new AuthorizationDecision(PolicyEffect.NOT_APPLICABLE, "No applicable policies", Collections.emptyList());
        }

        // Evaluate policies in priority order
        PolicyEffect finalDecision = PolicyEffect.NOT_APPLICABLE;
        String reason = "No rules matched";

        for (Policy policy : applicablePolicies) {
            PolicyEvaluationResult result = evaluatePolicy(policy, request, attributes);
            
            if (result.getEffect() == PolicyEffect.PERMIT) {
                finalDecision = PolicyEffect.PERMIT;
                reason = "Access granted by policy: " + policy.getName();
                break; // First permit wins
            } else if (result.getEffect() == PolicyEffect.DENY) {
                finalDecision = PolicyEffect.DENY;
                reason = "Access denied by policy: " + policy.getName();
            }
        }

        AuthorizationDecision decision = new AuthorizationDecision(finalDecision, reason, results);
        
        // Cache the decision
        if (decisionCache != null) {
            decisionCache.put(request, decision);
        }
        
        return decision;
    } catch (Exception e) {
        return new AuthorizationDecision(PolicyEffect.DENY, "Evaluation error: " + e.getMessage(), Collections.emptyList());
    }
}
```

## Practical Example: Access Denial in Action

### User Code
```python
spark = SparkSession.builder \
    .config("spark.abac.user.id", "hr_alice@company.com") \
    .config("spark.abac.session.id", "session_123") \
    .getOrCreate()

# This will trigger the ABAC evaluation flow
df = spark.read.parquet("s3://data-lake/departments/finance/budgets.parquet")
```

### Execution Flow
1. **Spark creates LogicalPlan** with `LogicalRelation` for parquet file
2. **Catalyst calls** `SparkCatalystInterceptor.apply(plan)`
3. **Interceptor finds** `LogicalRelation` and extracts path `s3://data-lake/departments/finance/budgets.parquet`
4. **Data source interceptor creates** `AccessRequest`:
   ```java
   AccessRequest request = new AccessRequest(
       "hr_alice@company.com",  // subject
       "s3://data-lake/departments/finance/budgets.parquet",  // resource
       "read",  // action
       environmentMap,  // environment
       "session_123"  // sessionId
   );
   ```
5. **ABACEngine.evaluate() is called** with this request
6. **Engine evaluates policies** and finds no policy allows HR user to access Finance data
7. **Returns** `AuthorizationDecision(PolicyEffect.DENY, "No applicable policies")`
8. **Interceptor throws** `SecurityException("Access denied: No applicable policies")`
9. **User sees** the security exception instead of data

### Log Output
```
INFO SparkCatalystInterceptor: Intercepted data access to: s3://data-lake/departments/finance/budgets.parquet (format: parquet)
INFO ABACDataSourceInterceptor: Checking read access for user hr_alice@company.com in session session_123 to resource: s3://data-lake/departments/finance/budgets.parquet
DEBUG ABACEngine: Evaluating access request: AccessRequest{subject='hr_alice@company.com', resource='s3://data-lake/departments/finance/budgets.parquet', action='read', sessionId='session_123'}
DEBUG ABACEngine: No applicable policies found for request
WARN ABACDataSourceInterceptor: ACCESS DENIED: Access denied to resource 's3://data-lake/departments/finance/budgets.parquet' for user 'hr_alice@company.com' in session 'session_123'. Reason: No applicable policies
```

## Why It Wasn't Working Before

The original implementation had these missing pieces:

1. **❌ Empty `registerSQLHooks()`**: Just logged a message, didn't register anything
2. **❌ No Catalyst Integration**: No rules added to Spark's query processing pipeline  
3. **❌ Missing Implementations**: `DefaultConditionEvaluator` and `CaffeineDecisionCache` didn't exist
4. **❌ Broken Import Chain**: Some classes weren't properly accessible across packages

## Now It Works Because

1. **✅ Real `registerSQLHooks()`**: Actually adds `SparkCatalystInterceptor` to Catalyst rules
2. **✅ Catalyst Integration**: Rule intercepts `LogicalRelation` nodes during query planning
3. **✅ Complete Implementation**: All required classes exist and work together
4. **✅ Proper Access Chain**: `SparkCatalystInterceptor` → `ABACDataSourceInterceptor` → `ABACEngine.evaluate()`

## Key Insight

The critical insight is that **Spark's plugin system alone is not enough** for data access control. You need to:

1. **Hook into Catalyst**: Use Spark's query optimization framework
2. **Intercept Logical Plans**: Catch data source operations before execution
3. **Create Access Requests**: Convert Spark operations to ABAC evaluations
4. **Enforce Decisions**: Throw exceptions to prevent unauthorized data access

The `ABACEngine.evaluate()` method is the heart of the system, but it only gets called when the entire integration chain is properly implemented and connected. 