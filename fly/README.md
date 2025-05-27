# Spark ABAC Solution

A comprehensive Attribute-Based Access Control (ABAC) system for Apache Spark workloads, providing fine-grained access control to data lake resources.

## Features

- **Fine-grained Access Control**: Attribute-based policies for data lake access
- **Multi-Session Support**: Different permissions per Spark session based on user identity
- **Dynamic Policy Updates**: Real-time policy enforcement without restart
- **Multiple Deployment Modes**: Support for Spark Notebooks, Streaming, and Batch jobs
- **Policy Management API**: RESTful API for policy CRUD operations
- **Audit Logging**: Comprehensive access logging and monitoring

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spark App     │    │  Policy Mgmt    │    │  Auth Service   │
│   (Notebook/    │    │     API         │    │                 │
│   Streaming/    │────┼─────────────────┼────┤                 │
│   Batch)        │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Spark ABAC     │    │  Policy Engine  │    │  Data Lake      │
│   Plugin        │◄───┤                 │────┤   (S3/HDFS/    │
│                 │    │                 │    │   Delta Lake)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Components

### 1. ABAC Policy Engine (`abac-engine/`)
- Policy definition and evaluation
- Attribute resolution
- Decision caching

### 2. Spark ABAC Plugin (`spark-plugin/`)
- Spark integration hooks
- Session-based access control
- Data source filtering

### 3. Policy Management API (`policy-api/`)
- RESTful policy management
- Policy validation
- Version control

### 4. Authentication Service (`auth-service/`)
- User authentication
- Attribute resolution
- Token management

### 5. Configuration (`config/`)
- Policy definitions
- Environment configurations
- Deployment scripts

## Quick Start

1. **Build the project**:
   ```bash
   ./build.sh
   ```

2. **Deploy the services**:
   ```bash
   docker-compose up -d
   ```

3. **Configure Spark**:
   ```bash
   # Add to spark-defaults.conf
   spark.plugins org.apache.spark.abac.SparkABACPlugin
   spark.abac.policy.service.url http://policy-api:8080
   spark.abac.auth.service.url http://auth-service:8081
   ```

4. **Create your first policy**:
   ```bash
   curl -X POST http://localhost:8080/api/policies \
     -H "Content-Type: application/json" \
     -d @examples/sample-policy.json
   ```

## Policy Examples

See `examples/` directory for sample policies covering:
- Department-based data access
- Time-based restrictions
- Role-based permissions
- Data sensitivity levels

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines.

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.
