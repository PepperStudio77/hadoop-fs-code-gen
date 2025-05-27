package com.spark.hdfs.sas.util;

import org.apache.hadoop.fs.Path;

/**
 * Utility class for path conversions between SAS scheme and actual storage paths
 */
public class PathUtils {
    
    /**
     * Convert a SAS filesystem path to the actual storage path
     * Example: sas://bucket/namespace/data/file.parquet -> s3://bucket/namespace/data/file.parquet
     */
    public static String convertToActualPath(Path sasPath) {
        String pathStr = sasPath.toString();
        
        // Remove the sas:// scheme and replace with appropriate storage scheme
        if (pathStr.startsWith("sas://")) {
            String withoutScheme = pathStr.substring(6); // Remove "sas://"
            
            // Determine the actual storage scheme based on path structure or configuration
            // This is a simplified implementation - in practice, you might want to:
            // 1. Use configuration to determine the default storage type
            // 2. Parse the path to determine storage type based on naming conventions
            // 3. Maintain a mapping of SAS paths to actual storage paths
            
            // For now, default to S3
            return "s3://" + withoutScheme;
        }
        
        // If it's already a full path (s3://, abfs://, etc.), return as-is
        return pathStr;
    }
    
    /**
     * Convert an actual storage path to a SAS filesystem path
     * Example: s3://bucket/namespace/data/file.parquet -> sas://bucket/namespace/data/file.parquet
     */
    public static String convertToSASPath(String actualPath) {
        if (actualPath.startsWith("s3://")) {
            return "sas://" + actualPath.substring(5);
        } else if (actualPath.startsWith("abfs://")) {
            return "sas://" + actualPath.substring(7);
        } else if (actualPath.startsWith("https://") && actualPath.contains(".blob.core.windows.net")) {
            // Convert Azure blob HTTPS URL to SAS path
            return "sas://" + actualPath.substring(8);
        }
        
        // If it's already a SAS path or unknown format, return as-is
        return actualPath;
    }
    
    /**
     * Extract the bucket/container name from a path
     */
    public static String extractBucket(String path) {
        String actualPath = convertToActualPath(new Path(path));
        
        if (actualPath.startsWith("s3://")) {
            String withoutScheme = actualPath.substring(5);
            int slashIndex = withoutScheme.indexOf('/');
            return slashIndex > 0 ? withoutScheme.substring(0, slashIndex) : withoutScheme;
        } else if (actualPath.startsWith("abfs://")) {
            String withoutScheme = actualPath.substring(7);
            int atIndex = withoutScheme.indexOf('@');
            return atIndex > 0 ? withoutScheme.substring(0, atIndex) : withoutScheme;
        }
        
        return null;
    }
    
    /**
     * Extract the key/blob name from a path
     */
    public static String extractKey(String path) {
        String actualPath = convertToActualPath(new Path(path));
        
        if (actualPath.startsWith("s3://")) {
            String withoutScheme = actualPath.substring(5);
            int slashIndex = withoutScheme.indexOf('/');
            return slashIndex > 0 && slashIndex < withoutScheme.length() - 1 
                ? withoutScheme.substring(slashIndex + 1) : "";
        } else if (actualPath.startsWith("abfs://")) {
            String withoutScheme = actualPath.substring(7);
            int slashIndex = withoutScheme.indexOf('/');
            return slashIndex > 0 && slashIndex < withoutScheme.length() - 1 
                ? withoutScheme.substring(slashIndex + 1) : "";
        }
        
        return path;
    }
    
    /**
     * Check if a path represents a directory (ends with /)
     */
    public static boolean isDirectory(String path) {
        return path.endsWith("/");
    }
    
    /**
     * Normalize a path by removing double slashes and ensuring proper format
     */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        
        // Replace multiple slashes with single slash, except for scheme part
        String normalized = path.replaceAll("(?<!:)//+", "/");
        
        // Remove trailing slash unless it's the root
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        return normalized;
    }
} 