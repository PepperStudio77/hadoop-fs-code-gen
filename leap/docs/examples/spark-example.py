#!/usr/bin/env python3
"""
Example Spark application using the SAS FileSystem

This example demonstrates how to use the custom SAS FileSystem
to read and write data from cloud storage with ABAC access control.
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, avg
import sys

def create_spark_session():
    """Create Spark session with SAS FileSystem configuration"""
    
    return SparkSession.builder \
        .appName("SAS FileSystem Example") \
        .config("spark.hadoop.fs.sas.service.url", "http://sas-service:8080") \
        .config("spark.hadoop.fs.sas.service.token", "/var/run/secrets/kubernetes.io/serviceaccount/token") \
        .config("spark.hadoop.fs.sas.cache.ttl.seconds", "300") \
        .config("spark.hadoop.fs.sas.default.expiry.hours", "1") \
        .getOrCreate()

def read_data_example(spark, input_path):
    """Example of reading data using SAS FileSystem"""
    
    print(f"Reading data from: {input_path}")
    
    try:
        # Read parquet file using SAS FileSystem
        df = spark.read.parquet(input_path)
        
        print(f"Successfully read {df.count()} rows")
        print("Schema:")
        df.printSchema()
        
        # Show sample data
        print("Sample data:")
        df.show(10)
        
        return df
        
    except Exception as e:
        print(f"Error reading data: {e}")
        return None

def write_data_example(spark, df, output_path):
    """Example of writing data using SAS FileSystem"""
    
    print(f"Writing data to: {output_path}")
    
    try:
        # Write parquet file using SAS FileSystem
        df.write \
          .mode("overwrite") \
          .parquet(output_path)
        
        print("Successfully wrote data")
        
    except Exception as e:
        print(f"Error writing data: {e}")

def data_processing_example(df):
    """Example data processing operations"""
    
    print("Performing data processing...")
    
    # Example aggregations
    summary = df.groupBy("category") \
                .agg(count("*").alias("count"),
                     avg("value").alias("avg_value")) \
                .orderBy("count", ascending=False)
    
    print("Summary by category:")
    summary.show()
    
    return summary

def main():
    """Main function"""
    
    if len(sys.argv) != 3:
        print("Usage: spark-example.py <input_path> <output_path>")
        print("Example: spark-example.py sas://my-bucket/namespace/input/ sas://my-bucket/namespace/output/")
        sys.exit(1)
    
    input_path = sys.argv[1]
    output_path = sys.argv[2]
    
    # Create Spark session
    spark = create_spark_session()
    
    try:
        # Read data
        df = read_data_example(spark, input_path)
        
        if df is not None:
            # Process data
            summary = data_processing_example(df)
            
            # Write results
            write_data_example(spark, summary, output_path)
        
    finally:
        # Stop Spark session
        spark.stop()

if __name__ == "__main__":
    main() 