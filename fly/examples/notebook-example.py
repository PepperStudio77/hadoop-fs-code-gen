# Spark ABAC Example - Jupyter Notebook
# This example demonstrates how the ABAC system works in a Spark notebook environment

from pyspark.sql import SparkSession
import os

# Initialize Spark session with ABAC plugin
spark = SparkSession.builder \
    .appName("ABAC Example - HR Data Analysis") \
    .config("spark.plugins", "org.apache.spark.abac.SparkABACPlugin") \
    .config("spark.abac.policy.service.url", "http://policy-api:8080") \
    .config("spark.abac.auth.service.url", "http://auth-service:8081") \
    .config("spark.abac.user.id", "hr_alice@company.com") \
    .config("spark.abac.session.id", "session_" + str(os.getpid())) \
    .getOrCreate()

print("Spark ABAC session initialized")
print(f"User: {spark.conf.get('spark.abac.user.id')}")
print(f"Session: {spark.conf.get('spark.abac.session.id')}")

# Example 1: HR user accessing HR department data (should succeed)
print("\n=== Example 1: HR user accessing HR data ===")
try:
    hr_data = spark.read.parquet("s3://data-lake/departments/hr/employees.parquet")
    print(f"✓ Successfully accessed HR data. Row count: {hr_data.count()}")
    hr_data.show(5)
except Exception as e:
    print(f"✗ Access denied: {e}")

# Example 2: HR user trying to access Finance data (should fail)
print("\n=== Example 2: HR user accessing Finance data ===")
try:
    finance_data = spark.read.parquet("s3://data-lake/departments/finance/budgets.parquet")
    print(f"✓ Successfully accessed Finance data. Row count: {finance_data.count()}")
    finance_data.show(5)
except Exception as e:
    print(f"✗ Access denied: {e}")

# Example 3: Time-based access control (only during business hours)
print("\n=== Example 3: Time-based access control ===")
from datetime import datetime
current_hour = datetime.now().hour
print(f"Current hour: {current_hour}")

try:
    sensitive_data = spark.read.parquet("s3://data-lake/departments/hr/sensitive/salaries.parquet")
    if 8 <= current_hour <= 18:
        print("✓ Access granted during business hours")
    else:
        print("? Access might be restricted outside business hours")
    sensitive_data.show(5)
except Exception as e:
    print(f"✗ Access denied: {e}")

# Example 4: Aggregated queries (should work with allowed data)
print("\n=== Example 4: Aggregated analysis ===")
try:
    # This query will only process data that the user has access to
    dept_summary = spark.sql("""
        SELECT department, COUNT(*) as employee_count, AVG(salary) as avg_salary
        FROM (
            SELECT 'HR' as department, salary FROM parquet.`s3://data-lake/departments/hr/employees.parquet`
            UNION ALL
            SELECT 'Finance' as department, salary FROM parquet.`s3://data-lake/departments/finance/employees.parquet`
        )
        GROUP BY department
    """)
    print("✓ Department summary analysis:")
    dept_summary.show()
except Exception as e:
    print(f"✗ Query failed: {e}")

# Clean up
spark.stop()
print("\nSpark session terminated") 