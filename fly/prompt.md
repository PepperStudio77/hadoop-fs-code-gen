# Fly mode

## Prompt
```
Help me implement an ABAC system for the Spark workload. Such a system should be able to configure the ABAC policy for data lake access. Spark workers should be able to access Datalake strictly following its ABAC policy. We should have a way to enforce such a policy in various scenarios, including Spark Notebook, Spark Streaming, or Spark Batch job submission. If multiple Spark sessions are running on the same instance, different Spark sessions based on their user identity should have different database permissions instead of sharing the same permission because they are sharing the underlying instance. The update to ABAC policies should be reflected in a production environment upon the successful update of the policy itself. 
```

## Follow up challenges:
* Can you explain to me that how do you deny a access from spark session when the spark users do not have corresponding ABAC policy to grant the access?
  - Cursor: Implement AttributionResolve.java but not been used. 
* why the evaluate function under ABACEngine.java  is never been called?
  - Cursor: Ack the gap. Implement  SparkCatalystInterceptor.java. and hook with Resolution batch in catalyst. 

