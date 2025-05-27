import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._

/**
 * Spark Streaming Example with ABAC Integration
 * This example shows how ABAC policies are enforced in streaming applications
 */
object ABACStreamingExample {
  def main(args: Array[String]): Unit = {
    // Initialize Spark session with ABAC plugin
    val spark = SparkSession.builder()
      .appName("ABAC Streaming Example")
      .config("spark.plugins", "org.apache.spark.abac.SparkABACPlugin")
      .config("spark.abac.policy.service.url", "http://policy-api:8080")
      .config("spark.abac.auth.service.url", "http://auth-service:8081")
      .config("spark.abac.user.id", "finance_bob@company.com")
      .config("spark.abac.session.id", s"streaming_session_${System.currentTimeMillis()}")
      .getOrCreate()

    import spark.implicits._

    // Define schema for incoming events
    val eventSchema = StructType(Seq(
      StructField("eventId", StringType, true),
      StructField("userId", StringType, true),
      StructField("department", StringType, true),
      StructField("action", StringType, true),
      StructField("resource", StringType, true),
      StructField("timestamp", TimestampType, true),
      StructField("amount", DoubleType, true)
    ))

    // Read streaming data from Kafka
    val eventStream = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "financial-events")
      .load()
      .select(from_json($"value".cast("string"), eventSchema).as("data"))
      .select("data.*")

    // Apply ABAC filtering - only process events the user has access to
    val filteredStream = eventStream
      .filter($"department" === "Finance" || $"department" === "General")
      .withWatermark("timestamp", "10 minutes")

    // Aggregate financial transactions (only accessible data)
    val aggregatedStream = filteredStream
      .groupBy(
        window($"timestamp", "5 minutes"),
        $"department"
      )
      .agg(
        count("*").as("transactionCount"),
        sum("amount").as("totalAmount"),
        avg("amount").as("avgAmount"),
        max("amount").as("maxAmount")
      )

    // Write results to secured output (ABAC policy will be checked)
    val query = aggregatedStream
      .writeStream
      .outputMode("update")
      .format("delta")
      .option("path", "s3://data-lake/departments/finance/aggregated/")
      .option("checkpointLocation", "s3://data-lake/checkpoints/finance-streaming/")
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .start()

    // Monitor the stream
    println("Starting ABAC-enabled streaming job...")
    println(s"User: ${spark.conf.get("spark.abac.user.id")}")
    println(s"Session: ${spark.conf.get("spark.abac.session.id")}")
    
    query.awaitTermination()
  }
} 