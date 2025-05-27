# Spark ABAC Solution Deployment Guide

This guide provides step-by-step instructions for deploying the Spark ABAC solution in different environments.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Local Development Setup](#local-development-setup)
- [Production Deployment](#production-deployment)
- [Configuration](#configuration)
- [Policy Management](#policy-management)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Software Requirements
- Java 11 or higher
- Maven 3.6+
- Docker 20.10+
- Docker Compose 2.0+
- Apache Spark 3.4.1+

### Infrastructure Requirements
- PostgreSQL 13+ (for policy storage)
- Redis 7+ (for decision caching)
- LDAP server (for user authentication)
- Container orchestration platform (Kubernetes/Docker Swarm)

## Local Development Setup

### 1. Build the Solution
```bash
# Clone the repository
git clone <repository-url>
cd spark-abac-solution

# Build all components
./build.sh

# Verify builds
ls target/jars/
```

### 2. Start Infrastructure Services
```bash
# Start supporting services
docker-compose up -d postgres redis ldap

# Wait for services to be ready
docker-compose logs -f postgres
```

### 3. Deploy ABAC Services
```bash
# Start policy and auth services
docker-compose up -d policy-api auth-service

# Check service health
curl http://localhost:8080/health
curl http://localhost:8081/health
```

### 4. Configure Spark
```bash
# Copy ABAC plugin to Spark
cp target/jars/spark-abac-plugin-1.0.0.jar $SPARK_HOME/jars/

# Update spark-defaults.conf
cat >> $SPARK_HOME/conf/spark-defaults.conf << EOF
spark.plugins                     org.apache.spark.abac.SparkABACPlugin
spark.abac.policy.service.url     http://localhost:8080
spark.abac.auth.service.url       http://localhost:8081
EOF
```

### 5. Load Sample Policies
```bash
# Create a sample policy
curl -X POST http://localhost:8080/api/policies \
  -H "Content-Type: application/json" \
  -d @examples/sample-policy.json

# Verify policy creation
curl http://localhost:8080/api/policies
```

## Production Deployment

### Kubernetes Deployment

#### 1. Namespace Setup
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: spark-abac
```

#### 2. ConfigMaps
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spark-abac-config
  namespace: spark-abac
data:
  spark-defaults.conf: |
    spark.plugins                     org.apache.spark.abac.SparkABACPlugin
    spark.abac.policy.service.url     http://policy-api:8080
    spark.abac.auth.service.url       http://auth-service:8081
    spark.abac.cache.enabled          true
    spark.abac.cache.ttl              300s
    spark.abac.audit.enabled          true
```

#### 3. Secrets
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: spark-abac-secrets
  namespace: spark-abac
type: Opaque
data:
  database-password: <base64-encoded-password>
  ldap-bind-password: <base64-encoded-password>
```

#### 4. Deploy Services
```bash
# Deploy PostgreSQL
kubectl apply -f k8s/postgres.yaml

# Deploy Redis
kubectl apply -f k8s/redis.yaml

# Deploy Policy API
kubectl apply -f k8s/policy-api.yaml

# Deploy Auth Service
kubectl apply -f k8s/auth-service.yaml

# Deploy Spark Operator (if using)
kubectl apply -f k8s/spark-operator.yaml
```

### Docker Swarm Deployment

```bash
# Initialize swarm (if not already done)
docker swarm init

# Deploy stack
docker stack deploy -c docker-compose.prod.yml spark-abac

# Check services
docker service ls
```

## Configuration

### Environment Variables

#### Policy API Service
```bash
# Database configuration
DATABASE_URL=jdbc:postgresql://postgres:5432/abac_policies
DATABASE_USERNAME=abac_user
DATABASE_PASSWORD=secure_password

# Service configuration
SERVER_PORT=8080
LOGGING_LEVEL_ROOT=INFO

# Cache configuration
REDIS_HOST=redis
REDIS_PORT=6379
```

#### Auth Service
```bash
# LDAP configuration
LDAP_URL=ldap://ldap:389
LDAP_BASE_DN=dc=company,dc=com
LDAP_BIND_DN=cn=admin,dc=company,dc=com
LDAP_BIND_PASSWORD=admin_password

# Service configuration
SERVER_PORT=8081
```

#### Spark Configuration
```bash
# ABAC Plugin configuration
spark.plugins=org.apache.spark.abac.SparkABACPlugin
spark.abac.policy.service.url=http://policy-api:8080
spark.abac.auth.service.url=http://auth-service:8081

# Cache settings
spark.abac.cache.enabled=true
spark.abac.cache.ttl=300s
spark.abac.cache.max.size=10000

# Audit settings
spark.abac.audit.enabled=true
spark.abac.audit.log.level=INFO
```

## Policy Management

### Creating Policies via API

```bash
# Create a new policy
curl -X POST http://policy-api:8080/api/policies \
  -H "Content-Type: application/json" \
  -d '{
    "id": "policy-002",
    "name": "Time-Based Access Policy",
    "description": "Restricts access based on time of day",
    "enabled": true,
    "priority": 50,
    "defaultEffect": "DENY",
    "rules": [
      {
        "id": "rule-time-001",
        "name": "Business Hours Access",
        "effect": "PERMIT",
        "conditions": [
          {
            "expression": "T(java.time.LocalTime).now().getHour() >= 8 && T(java.time.LocalTime).now().getHour() <= 18"
          }
        ]
      }
    ]
  }'
```

### Policy Updates

```bash
# Update existing policy
curl -X PUT http://policy-api:8080/api/policies/policy-002 \
  -H "Content-Type: application/json" \
  -d @updated-policy.json

# Enable/disable policy
curl -X PATCH http://policy-api:8080/api/policies/policy-002/enable
curl -X PATCH http://policy-api:8080/api/policies/policy-002/disable
```

### Bulk Policy Management

```bash
# Import policies from file
curl -X POST http://policy-api:8080/api/policies/import \
  -H "Content-Type: application/json" \
  -d @bulk-policies.json

# Export all policies
curl http://policy-api:8080/api/policies/export > policies-backup.json
```

## Monitoring

### Health Checks

```bash
# Check policy service health
curl http://policy-api:8080/actuator/health

# Check auth service health
curl http://auth-service:8081/actuator/health

# Check policy cache status
curl http://policy-api:8080/actuator/metrics/cache.size
```

### Metrics Collection

#### Prometheus Configuration
```yaml
scrape_configs:
  - job_name: 'spark-abac-policy-api'
    static_configs:
      - targets: ['policy-api:8080']
    metrics_path: /actuator/prometheus

  - job_name: 'spark-abac-auth-service'
    static_configs:
      - targets: ['auth-service:8081']
    metrics_path: /actuator/prometheus
```

#### Grafana Dashboard
- Import dashboard from `monitoring/grafana-dashboard.json`
- Configure data source to point to Prometheus
- Set up alerts for policy evaluation failures

### Log Aggregation

#### ELK Stack Configuration
```yaml
filebeat.inputs:
  - type: container
    paths:
      - '/var/lib/docker/containers/*/*.log'
    processors:
      - add_docker_metadata:
          host: "unix:///var/run/docker.sock"

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
```

## Troubleshooting

### Common Issues

#### 1. Plugin Not Loading
```bash
# Check if plugin JAR is in classpath
ls $SPARK_HOME/jars/ | grep abac

# Verify plugin configuration
spark-submit --conf spark.plugins=org.apache.spark.abac.SparkABACPlugin \
  --class org.apache.spark.examples.SparkPi \
  --master local[2] \
  $SPARK_HOME/examples/jars/spark-examples_2.12-3.4.1.jar 10
```

#### 2. Policy Service Connection Issues
```bash
# Test connectivity
curl -v http://policy-api:8080/api/policies

# Check DNS resolution
nslookup policy-api

# Verify network policies (Kubernetes)
kubectl get networkpolicies -n spark-abac
```

#### 3. Authentication Failures
```bash
# Test LDAP connection
ldapsearch -x -H ldap://ldap:389 -D "cn=admin,dc=company,dc=com" -W -b "dc=company,dc=com"

# Check auth service logs
docker logs auth-service

# Verify user attributes
curl http://auth-service:8081/api/users/alice@company.com/attributes
```

#### 4. Policy Evaluation Issues
```bash
# Enable debug logging
export SPARK_CONF_DIR=/path/to/conf
echo "log4j.logger.org.apache.spark.abac=DEBUG" >> $SPARK_CONF_DIR/log4j.properties

# Test policy evaluation
curl -X POST http://policy-api:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "alice@company.com",
    "resource": "s3://data-lake/departments/hr/employees.parquet",
    "action": "read",
    "environment": {}
  }'
```

### Performance Tuning

#### 1. Cache Optimization
```bash
# Increase cache size for high-throughput environments
spark.abac.cache.max.size=50000
spark.abac.cache.ttl=600s

# Enable cache warming
spark.abac.cache.warm.enabled=true
```

#### 2. Policy Service Scaling
```yaml
# Kubernetes HPA for policy service
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: policy-api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: policy-api
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

#### 3. Database Optimization
```sql
-- Create indexes for policy queries
CREATE INDEX idx_policies_enabled ON policies(enabled);
CREATE INDEX idx_policies_priority ON policies(priority DESC);
CREATE INDEX idx_rules_policy_id ON rules(policy_id);
```

## Security Considerations

### 1. Network Security
- Use TLS for all service communications
- Implement network segmentation
- Configure firewalls and security groups

### 2. Secret Management
- Use Kubernetes secrets or external secret managers
- Rotate credentials regularly
- Implement least privilege access

### 3. Audit Logging
- Enable comprehensive audit logging
- Centralize log collection
- Implement log monitoring and alerting

### 4. Policy Validation
- Implement policy syntax validation
- Test policies in staging environment
- Use policy versioning and rollback capabilities 