# Spark HDFS SAS Project Makefile

.PHONY: all build test clean deploy undeploy docker-build docker-push help

# Variables
BACKEND_IMAGE ?= sas-service:latest
JAVA_CLIENT_JAR = java-client/target/spark-hdfs-sas-client-1.0.0.jar
NAMESPACE ?= default

# Default target
all: build

# Build all components
build: build-backend build-java

# Build backend service
build-backend:
	@echo "Building backend service..."
	cd backend-service && go mod tidy
	cd backend-service && go build -o bin/sas-service cmd/server/main.go

# Build Java client
build-java:
	@echo "Building Java client..."
	cd java-client && mvn clean package

# Run tests
test: test-backend test-java

# Test backend service
test-backend:
	@echo "Testing backend service..."
	cd backend-service && go test ./...

# Test Java client
test-java:
	@echo "Testing Java client..."
	cd java-client && mvn test

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	cd backend-service && rm -rf bin/
	cd java-client && mvn clean

# Build Docker image
docker-build:
	@echo "Building Docker image..."
	cd backend-service && docker build -t $(BACKEND_IMAGE) .

# Push Docker image
docker-push: docker-build
	@echo "Pushing Docker image..."
	docker push $(BACKEND_IMAGE)

# Deploy to Kubernetes
deploy:
	@echo "Deploying to Kubernetes..."
	kubectl apply -f backend-service/deployments/deployment.yaml -n $(NAMESPACE)

# Undeploy from Kubernetes
undeploy:
	@echo "Undeploying from Kubernetes..."
	kubectl delete -f backend-service/deployments/deployment.yaml -n $(NAMESPACE) --ignore-not-found

# Create secrets for cloud storage credentials
create-secrets:
	@echo "Creating Kubernetes secrets..."
	@echo "Note: Update the values below with your actual credentials"
	kubectl create secret generic aws-credentials \
		--from-literal=access-key-id=YOUR_AWS_ACCESS_KEY \
		--from-literal=secret-access-key=YOUR_AWS_SECRET_KEY \
		-n $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -
	kubectl create secret generic azure-credentials \
		--from-literal=account-name=YOUR_AZURE_ACCOUNT \
		--from-literal=account-key=YOUR_AZURE_KEY \
		-n $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -

# Run example Spark application
run-example:
	@echo "Running example Spark application..."
	spark-submit \
		--jars $(JAVA_CLIENT_JAR) \
		--conf spark.hadoop.fs.sas.service.url=http://sas-service:8080 \
		--conf spark.hadoop.fs.sas.service.token=$$(cat /var/run/secrets/kubernetes.io/serviceaccount/token) \
		docs/examples/spark-example.py \
		sas://my-bucket/namespace/input/ \
		sas://my-bucket/namespace/output/

# Development setup
dev-setup:
	@echo "Setting up development environment..."
	@echo "Installing Go dependencies..."
	cd backend-service && go mod download
	@echo "Installing Java dependencies..."
	cd java-client && mvn dependency:resolve

# Format code
format:
	@echo "Formatting code..."
	cd backend-service && go fmt ./...
	cd java-client && mvn spotless:apply

# Lint code
lint:
	@echo "Linting code..."
	cd backend-service && golangci-lint run
	cd java-client && mvn spotless:check

# Generate documentation
docs:
	@echo "Generating documentation..."
	cd backend-service && godoc -http=:6060 &
	@echo "Go documentation available at http://localhost:6060"

# Check service health
health-check:
	@echo "Checking service health..."
	kubectl get pods -l app=sas-service -n $(NAMESPACE)
	kubectl logs -l app=sas-service -n $(NAMESPACE) --tail=10

# Port forward for local development
port-forward:
	@echo "Port forwarding sas-service to localhost:8080..."
	kubectl port-forward service/sas-service 8080:8080 -n $(NAMESPACE)

# Show logs
logs:
	@echo "Showing service logs..."
	kubectl logs -l app=sas-service -n $(NAMESPACE) -f

# Help
help:
	@echo "Available targets:"
	@echo "  all           - Build all components"
	@echo "  build         - Build backend and Java client"
	@echo "  build-backend - Build backend service only"
	@echo "  build-java    - Build Java client only"
	@echo "  test          - Run all tests"
	@echo "  test-backend  - Run backend tests only"
	@echo "  test-java     - Run Java tests only"
	@echo "  clean         - Clean build artifacts"
	@echo "  docker-build  - Build Docker image"
	@echo "  docker-push   - Build and push Docker image"
	@echo "  deploy        - Deploy to Kubernetes"
	@echo "  undeploy      - Remove from Kubernetes"
	@echo "  create-secrets- Create Kubernetes secrets for cloud credentials"
	@echo "  run-example   - Run example Spark application"
	@echo "  dev-setup     - Setup development environment"
	@echo "  format        - Format code"
	@echo "  lint          - Lint code"
	@echo "  docs          - Generate documentation"
	@echo "  health-check  - Check service health"
	@echo "  port-forward  - Port forward service for local access"
	@echo "  logs          - Show service logs"
	@echo "  help          - Show this help message" 