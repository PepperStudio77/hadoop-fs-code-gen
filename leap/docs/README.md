# Spark HDFS SAS Project

A custom HDFS FileSystem implementation for Apache Spark that provides secure access to cloud storage (S3, Azure Blob) through SAS (Shared Access Signature) URLs with Kubernetes authentication and ABAC (Attribute-Based Access Control) using Rego policies.

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Spark Client  │    │  Backend Service │    │  Cloud Storage  │
│                 │    │                  │    │                 │
│ ┌─────────────┐ │    │ ┌──────────────┐ │    │ ┌─────────────┐ │
│ │ SAS FileSystem│◄────┤ │ SAS Service  │ │    │ │     S3      │ │
│ │             │ │    │ │              │ │    │ │   Azure     │ │
│ └─────────────┘ │    │ └──────────────┘ │    │ └─────────────┘ │
│                 │    │ ┌──────────────┐ │    │                 │
│                 │    │ │ Rego Engine  │ │    │                 │
│                 │    │ │ (ABAC)       │ │    │                 │
│                 │    │ └──────────────┘ │    │                 │
│                 │    │ ┌──────────────┐ │    │                 │
│                 │    │ │ K8s Auth     │ │    │                 │
│                 │    │ │              │ │    │                 │
│                 │    │ └──────────────┘ │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Components

### 1. Backend Service (Go)
- **K8s Authentication**: Validates service account tokens
- **Rego Policy Engine**: Implements ABAC for fine-grained access control
- **Storage Integration**: Generates SAS URLs for S3 and Azure Blob Storage
- **REST API**: Provides endpoints for SAS URL generation and policy management

### 2. Java Client (HDFS FileSystem)
- **Custom FileSystem**: Implements Hadoop FileSystem interface
- **SAS URL Integration**: Translates HDFS operations to SAS-authenticated requests
- **Caching**: Caches SAS URLs to reduce backend calls
- **Transparent Integration**: Works seamlessly with existing Spark applications

## Features

- ✅ **Kubernetes Authentication**: Service account token validation
- ✅ **ABAC with Rego**: Flexible, policy-based access control
- ✅ **Multi-Cloud Support**: S3 and Azure Blob Storage
- ✅ **SAS URL Generation**: Secure, time-limited access URLs
- ✅ **HDFS Compatibility**: Drop-in replacement for standard HDFS
- ✅ **Caching**: Intelligent caching of SAS URLs
- ✅ **Logging**: Comprehensive logging for debugging and monitoring

## Quick Start

### 1. Deploy Backend Service

```bash
# Build the backend service
cd backend-service
go build -o bin/sas-service cmd/server/main.go

# Deploy to Kubernetes
kubectl apply -f deployments/
```

### 2. Build Java Client

```bash
# Build the Java client
cd java-client
mvn clean package

# The JAR will be available at target/spark-hdfs-sas-client-1.0.0.jar
```

### 3. Configure Spark

```bash
# Add the JAR to Spark classpath
spark-submit \
  --jars target/spark-hdfs-sas-client-1.0.0.jar \
  --conf spark.hadoop.fs.sas.service.url=http://sas-service:8080 \
  --conf spark.hadoop.fs.sas.service.token=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token) \
  your-spark-application.py
```

### 4. Use in Spark Code

```python
# Python
df = spark.read.parquet("sas://my-bucket/namespace/data/file.parquet")
df.write.parquet("sas://my-bucket/namespace/output/")
```

```scala
// Scala
val df = spark.read.parquet("sas://my-bucket/namespace/data/file.parquet")
df.write.parquet("sas://my-bucket/namespace/output/")
```

## Configuration

### Backend Service Configuration

```yaml
# configs/config.yaml
server:
  port: "8080"
  host: "0.0.0.0"

k8s:
  in_cluster: true
  namespace: "default"

storage:
  aws:
    region: "us-west-2"
  azure:
    container_name: "spark-data"

rego:
  policy_path: "./policies"
```

### Spark Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `fs.sas.service.url` | Backend service URL | Required |
| `fs.sas.service.token` | K8s service account token | Required |
| `fs.sas.cache.ttl.seconds` | SAS URL cache TTL | 300 |
| `fs.sas.default.expiry.hours` | Default SAS URL expiry | 1 |

## Access Control Policies

The system uses Rego policies for access control. Example policy:

```rego
package authz

# Allow read access to data in user's namespace
allow {
    input.operation == "read"
    input.user.namespace == extract_namespace_from_path(input.resource)
}

# Allow write access for specific service accounts
allow {
    input.operation == "write"
    input.user.service_account in ["spark-driver", "spark-executor"]
}
```

## API Reference

### Generate SAS URL

```http
POST /api/v1/sas
Authorization: Bearer <k8s-token>
Content-Type: application/json

{
  "path": "s3://bucket/namespace/data/file.parquet",
  "operation": "read",
  "expiry_hours": 1,
  "metadata": {}
}
```

### List Policies

```http
GET /api/v1/policies
Authorization: Bearer <k8s-token>
```

### Reload Policies

```http
POST /api/v1/policies/reload
Authorization: Bearer <k8s-token>
```

## Security Considerations

1. **Token Security**: Service account tokens should be rotated regularly
2. **Network Security**: Use TLS for all communications
3. **Policy Validation**: Regularly audit and test access policies
4. **SAS URL Expiry**: Use short expiry times for SAS URLs
5. **Logging**: Monitor access patterns and failed requests

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Check service account token validity
   - Verify RBAC permissions in Kubernetes

2. **Access Denied**
   - Review Rego policies
   - Check namespace and service account mappings

3. **SAS URL Errors**
   - Verify cloud storage credentials
   - Check SAS URL expiry times

### Debugging

Enable debug logging:

```bash
# Backend service
export LOG_LEVEL=debug

# Spark client
--conf spark.hadoop.fs.sas.log.level=DEBUG
```

## Development

### Building from Source

```bash
# Backend service
cd backend-service
go mod tidy
go build -o bin/sas-service cmd/server/main.go

# Java client
cd java-client
mvn clean compile test package
```

### Running Tests

```bash
# Backend service
cd backend-service
go test ./...

# Java client
cd java-client
mvn test
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details. 