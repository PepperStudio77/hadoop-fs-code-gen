# Access Denial Scenarios in Spark ABAC

This document demonstrates how the Spark ABAC system denies access when users don't have the proper policies.

## Scenario 1: No Applicable Policies Found

### Setup
```python
# User tries to access data with no policies configured for them
spark = SparkSession.builder \
    .appName("Test Access Denial") \
    .config("spark.plugins", "org.apache.spark.abac.SparkABACPlugin") \
    .config("spark.abac.user.id", "unknown_user@company.com") \
    .config("spark.abac.session.id", "test_session_001") \
    .getOrCreate()
```

### Access Attempt
```python
try:
    # User tries to read finance data but has no policies
    df = spark.read.parquet("s3://data-lake/departments/finance/budgets.parquet")
    print("Access granted - this shouldn't happen!")
except Exception as e:
    print(f"Access denied: {e}")
```

### Expected Output
```
INFO ABACDataSourceInterceptor: Checking read access for user unknown_user@company.com 
     in session test_session_001 to resource: s3://data-lake/departments/finance/budgets.parquet
INFO ABACEngine: No applicable policies found for request: AccessRequest{subject='unknown_user@company.com', 
     resource='s3://data-lake/departments/finance/budgets.parquet', action='read', sessionId='test_session_001'}
WARN ABACDataSourceInterceptor: ACCESS DENIED: Access denied to resource 
     's3://data-lake/departments/finance/budgets.parquet' for user 'unknown_user@company.com' 
     in session 'test_session_001'. Reason: No applicable policies
SecurityException: Access denied to resource 's3://data-lake/departments/finance/budgets.parquet' 
     for user 'unknown_user@company.com' in session 'test_session_001'. Reason: No applicable policies
```

## Scenario 2: Explicit Policy Denial

### Policy Configuration
```json
{
  "id": "policy-finance-restrict",
  "name": "Finance Data Restriction",
  "enabled": true,
  "priority": 100,
  "defaultEffect": "DENY",
  "target": {
    "resources": ["s3://data-lake/departments/finance/*"],
    "actions": ["read", "write"],
    "subjects": ["*"]
  },
  "rules": [
    {
      "id": "rule-deny-non-finance",
      "name": "Deny Non-Finance Users",
      "effect": "DENY",
      "conditions": [
        {
          "attributeName": "user.department",
          "operator": "NOT_EQUALS",
          "expectedValue": "Finance"
        }
      ]
    }
  ]
}
```

### Access Attempt by HR User
```python
spark = SparkSession.builder \
    .config("spark.abac.user.id", "hr_alice@company.com") \
    .getOrCreate()

try:
    df = spark.read.parquet("s3://data-lake/departments/finance/budgets.parquet")
except Exception as e:
    print(f"Access denied: {e}")
```

### Expected Output
```
INFO ABACEngine: Evaluating policy: Finance Data Restriction
INFO ABACEngine: Rule Deny Non-Finance Users denied access
WARN ABACDataSourceInterceptor: ACCESS DENIED: Access denied to resource 
     's3://data-lake/departments/finance/budgets.parquet' for user 'hr_alice@company.com' 
     in session 'hr_session_001'. Reason: Access denied by policy: Finance Data Restriction
SecurityException: Access denied to resource 's3://data-lake/departments/finance/budgets.parquet' 
     for user 'hr_alice@company.com' in session 'hr_session_001'. 
     Reason: Access denied by policy: Finance Data Restriction
```

## Scenario 3: Time-Based Access Denial

### Policy with Time Restrictions
```json
{
  "id": "policy-business-hours",
  "name": "Business Hours Only Access",
  "enabled": true,
  "rules": [
    {
      "id": "rule-business-hours",
      "name": "Allow Business Hours Only",
      "effect": "PERMIT",
      "conditions": [
        {
          "expression": "T(java.time.LocalTime).now().getHour() >= 8 && T(java.time.LocalTime).now().getHour() <= 18"
        }
      ]
    }
  ]
}
```

### Access Attempt Outside Business Hours
```python
# If run at 10 PM (22:00)
try:
    df = spark.read.parquet("s3://data-lake/sensitive/payroll.parquet")
except Exception as e:
    print(f"Access denied: {e}")
```

### Expected Output
```
INFO DefaultAttributeResolver: Current hour: 22
INFO ABACEngine: Evaluating rule: Allow Business Hours Only
INFO ABACEngine: Conditions not satisfied for time-based access
WARN ABACDataSourceInterceptor: ACCESS DENIED: Access denied to resource 
     's3://data-lake/sensitive/payroll.parquet' for user 'finance_bob@company.com' 
     in session 'finance_session_002'. Reason: Time restriction - outside business hours
SecurityException: Access denied to resource 's3://data-lake/sensitive/payroll.parquet' 
     for user 'finance_bob@company.com' in session 'finance_session_002'. 
     Reason: Time restriction - outside business hours
```

## How Access Denial Works Technically

### 1. Interception Points

The ABAC system intercepts Spark operations at these levels:

```python
# When user calls:
df = spark.read.parquet("s3://data-lake/finance/data.parquet")

# The system intercepts at:
# 1. DataFrameReader.parquet() method
# 2. Catalyst plan creation
# 3. Physical plan execution
# 4. File system access layer
```

### 2. Access Flow During Denial

```
User Code: spark.read.parquet("s3://path/to/data")
    ↓
Spark ABAC Plugin Intercepts
    ↓
ABACDataSourceInterceptor.checkReadAccess()
    ↓
Create AccessRequest{user, resource, action, session}
    ↓
ABACEngine.evaluate(request)
    ↓
PolicyStore.getAllPolicies() → Check applicable policies
    ↓
AttributeResolver.resolve() → Get user/resource attributes
    ↓
ConditionEvaluator.evaluate() → Check rule conditions
    ↓
AuthorizationDecision{effect=DENY/NOT_APPLICABLE, reason="..."}
    ↓
if (!decision.isPermitted()) → throw SecurityException
    ↓
User sees: SecurityException with detailed error message
```

### 3. Different Types of Denial

| Denial Type | Cause | Example Message |
|-------------|-------|-----------------|
| **NO_APPLICABLE_POLICY** | No policies found for user/resource | "No applicable policies" |
| **EXPLICIT_DENY** | Policy rule explicitly denies access | "Access denied by policy: Finance Restriction" |
| **CONDITION_FAILED** | Policy exists but conditions not met | "Time restriction - outside business hours" |
| **INSUFFICIENT_CLEARANCE** | User clearance level too low | "Insufficient clearance level (2 < 4 required)" |

### 4. Session Isolation During Denial

```python
# Session 1: Finance user
session1 = SparkSession.builder() \
    .config("spark.abac.user.id", "finance_alice@company.com") \
    .config("spark.abac.session.id", "session_001") \
    .getOrCreate()

# Session 2: HR user  
session2 = SparkSession.builder() \
    .config("spark.abac.user.id", "hr_bob@company.com") \
    .config("spark.abac.session.id", "session_002") \
    .getOrCreate()

# Same code, different outcomes based on user identity:
try:
    # This works for finance_alice
    df1 = session1.read.parquet("s3://data-lake/departments/finance/budgets.parquet")
    print("Finance user: Access granted")
except:
    print("Finance user: Access denied")

try:
    # This fails for hr_bob
    df2 = session2.read.parquet("s3://data-lake/departments/finance/budgets.parquet") 
    print("HR user: Access granted")
except:
    print("HR user: Access denied")
```

## Error Handling and User Experience

### User-Friendly Error Messages

Instead of cryptic errors, users see clear messages:

```python
# Bad: Generic error
# FileNotFoundException: Path s3://data-lake/finance/data.parquet does not exist

# Good: ABAC-aware error
# SecurityException: Access denied to resource 's3://data-lake/finance/data.parquet' 
# for user 'hr_alice@company.com'. Reason: Insufficient clearance level. 
# Contact your administrator to request access.
```

### Audit Trail

Every access denial is logged for security monitoring:

```json
{
  "timestamp": "2024-01-15T14:30:00Z",
  "event": "ACCESS_DENIED",
  "user": "hr_alice@company.com",
  "session": "session_12345",
  "resource": "s3://data-lake/finance/budgets.parquet",
  "action": "read",
  "reason": "No applicable policies",
  "policy_evaluated": [],
  "user_attributes": {
    "department": "HR",
    "clearance_level": 2
  },
  "resource_attributes": {
    "sensitivity_level": 4,
    "data_classification": "confidential"
  }
}
```

This approach ensures that:
1. **Security**: No unauthorized access can occur
2. **Transparency**: Users understand why access was denied  
3. **Auditability**: All access attempts are logged
4. **Session Isolation**: Each session enforces its own user's permissions
5. **Performance**: Denials happen early in the process before expensive operations 