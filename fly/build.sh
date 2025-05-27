#!/bin/bash

set -e

echo "Building Spark ABAC Solution..."

# Create directories if they don't exist
mkdir -p target/jars
mkdir -p target/docker

# Build ABAC Policy Engine
echo "Building ABAC Policy Engine..."
cd abac-engine
mvn clean package -DskipTests
cp target/*.jar ../target/jars/
cd ..

# Build Spark Plugin
echo "Building Spark ABAC Plugin..."
cd spark-plugin
mvn clean package -DskipTests
cp target/*.jar ../target/jars/
cd ..

# Build Policy Management API
echo "Building Policy Management API..."
cd policy-api
mvn clean package -DskipTests
cp target/*.jar ../target/jars/
cd ..

# Build Authentication Service
echo "Building Authentication Service..."
cd auth-service
mvn clean package -DskipTests
cp target/*.jar ../target/jars/
cd ..

# Build Docker images
echo "Building Docker images..."
docker build -t spark-abac/policy-api:latest -f policy-api/Dockerfile .
docker build -t spark-abac/auth-service:latest -f auth-service/Dockerfile .

echo "Build completed successfully!"
echo "JAR files are available in target/jars/"
echo "Docker images are tagged with 'latest'" 