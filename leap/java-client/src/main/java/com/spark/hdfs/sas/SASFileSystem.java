package com.spark.hdfs.sas;

import com.spark.hdfs.sas.client.SASClient;
import com.spark.hdfs.sas.client.SASResponse;
import com.spark.hdfs.sas.util.PathUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Custom HDFS FileSystem implementation that uses SAS URLs for accessing cloud storage.
 * This filesystem intercepts HDFS calls and translates them to SAS-authenticated requests
 * to the backend service.
 */
public class SASFileSystem extends FileSystem {
    
    private static final Logger LOG = LoggerFactory.getLogger(SASFileSystem.class);
    
    public static final String SCHEME = "sas";
    
    // Configuration keys
    public static final String SAS_SERVICE_URL = "fs.sas.service.url";
    public static final String SAS_SERVICE_TOKEN = "fs.sas.service.token";
    public static final String SAS_CACHE_TTL_SECONDS = "fs.sas.cache.ttl.seconds";
    public static final String SAS_DEFAULT_EXPIRY_HOURS = "fs.sas.default.expiry.hours";
    
    private URI uri;
    private Path workingDirectory;
    private SASClient sasClient;
    private final ConcurrentMap<String, CachedSASResponse> sasCache = new ConcurrentHashMap<>();
    
    private static class CachedSASResponse {
        final SASResponse response;
        final long cacheTime;
        final long ttlMillis;
        
        CachedSASResponse(SASResponse response, long ttlMillis) {
            this.response = response;
            this.cacheTime = System.currentTimeMillis();
            this.ttlMillis = ttlMillis;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > ttlMillis;
        }
    }
    
    @Override
    public void initialize(URI uri, Configuration conf) throws IOException {
        super.initialize(uri, conf);
        
        this.uri = uri;
        this.workingDirectory = new Path("/user", System.getProperty("user.name"));
        
        // Initialize SAS client
        String serviceUrl = conf.get(SAS_SERVICE_URL);
        String serviceToken = conf.get(SAS_SERVICE_TOKEN);
        
        if (serviceUrl == null) {
            throw new IOException("SAS service URL not configured. Set " + SAS_SERVICE_URL);
        }
        
        if (serviceToken == null) {
            throw new IOException("SAS service token not configured. Set " + SAS_SERVICE_TOKEN);
        }
        
        this.sasClient = new SASClient(serviceUrl, serviceToken);
        
        LOG.info("Initialized SAS FileSystem with service URL: {}", serviceUrl);
    }
    
    @Override
    public URI getUri() {
        return uri;
    }
    
    @Override
    public FSDataInputStream open(Path path, int bufferSize) throws IOException {
        LOG.debug("Opening file for read: {}", path);
        
        String actualPath = PathUtils.convertToActualPath(path);
        SASResponse sasResponse = getSASURL(actualPath, "read");
        
        // Create input stream using the SAS URL
        return new FSDataInputStream(new SASInputStream(sasResponse.getUrl(), bufferSize));
    }
    
    @Override
    public FSDataOutputStream create(Path path, FsPermission permission, boolean overwrite,
                                   int bufferSize, short replication, long blockSize,
                                   Progressable progress) throws IOException {
        LOG.debug("Creating file for write: {}", path);
        
        if (!overwrite && exists(path)) {
            throw new FileAlreadyExistsException("File already exists: " + path);
        }
        
        String actualPath = PathUtils.convertToActualPath(path);
        SASResponse sasResponse = getSASURL(actualPath, "write");
        
        // Create output stream using the SAS URL
        return new FSDataOutputStream(new SASOutputStream(sasResponse.getUrl(), bufferSize), statistics);
    }
    
    @Override
    public FSDataOutputStream append(Path path, int bufferSize, Progressable progress) throws IOException {
        throw new UnsupportedOperationException("Append is not supported by SAS FileSystem");
    }
    
    @Override
    public boolean rename(Path src, Path dst) throws IOException {
        LOG.debug("Renaming {} to {}", src, dst);
        
        // For cloud storage, rename is typically implemented as copy + delete
        // This is a simplified implementation
        if (!exists(src)) {
            return false;
        }
        
        if (exists(dst)) {
            return false;
        }
        
        // Copy file content
        try (FSDataInputStream in = open(src, 4096);
             FSDataOutputStream out = create(dst, FsPermission.getFileDefault(), false, 4096, (short) 1, 4096, null)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        // Delete source
        return delete(src, false);
    }
    
    @Override
    public boolean delete(Path path, boolean recursive) throws IOException {
        LOG.debug("Deleting path: {} (recursive: {})", path, recursive);
        
        String actualPath = PathUtils.convertToActualPath(path);
        
        try {
            SASResponse sasResponse = getSASURL(actualPath, "delete");
            // Use the SAS URL to delete the file
            // This would typically involve making an HTTP DELETE request
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to delete path: {}", path, e);
            return false;
        }
    }
    
    @Override
    public FileStatus[] listStatus(Path path) throws FileNotFoundException, IOException {
        LOG.debug("Listing status for path: {}", path);
        
        // For simplicity, this implementation returns a basic file status
        // In a real implementation, you would need to query the backend service
        // for directory listings or maintain metadata
        
        if (!exists(path)) {
            throw new FileNotFoundException("Path not found: " + path);
        }
        
        // Return a single file status for the path itself
        return new FileStatus[] { getFileStatus(path) };
    }
    
    @Override
    public void setWorkingDirectory(Path newDir) {
        this.workingDirectory = newDir;
    }
    
    @Override
    public Path getWorkingDirectory() {
        return workingDirectory;
    }
    
    @Override
    public boolean mkdirs(Path path, FsPermission permission) throws IOException {
        LOG.debug("Creating directories: {}", path);
        // For cloud storage, directories are typically virtual
        return true;
    }
    
    @Override
    public FileStatus getFileStatus(Path path) throws IOException {
        LOG.debug("Getting file status for: {}", path);
        
        String actualPath = PathUtils.convertToActualPath(path);
        
        // For simplicity, return a basic file status
        // In a real implementation, you would query the backend for metadata
        return new FileStatus(
            0, // length - would need to be fetched from backend
            false, // isDir
            1, // replication
            4096, // blockSize
            System.currentTimeMillis(), // modificationTime
            System.currentTimeMillis(), // accessTime
            FsPermission.getFileDefault(), // permission
            System.getProperty("user.name"), // owner
            System.getProperty("user.name"), // group
            path
        );
    }
    
    /**
     * Get SAS URL for the given path and operation, with caching
     */
    private SASResponse getSASURL(String path, String operation) throws IOException {
        String cacheKey = path + ":" + operation;
        
        // Check cache first
        CachedSASResponse cached = sasCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            LOG.debug("Using cached SAS URL for {} ({})", path, operation);
            return cached.response;
        }
        
        // Get fresh SAS URL from service
        LOG.debug("Requesting SAS URL for {} ({})", path, operation);
        SASResponse response = sasClient.getSASURL(path, operation);
        
        // Cache the response
        long cacheTtlSeconds = getConf().getLong(SAS_CACHE_TTL_SECONDS, 300); // 5 minutes default
        sasCache.put(cacheKey, new CachedSASResponse(response, cacheTtlSeconds * 1000));
        
        return response;
    }
    
    /**
     * Check if a path exists by attempting to get its file status
     */
    private boolean exists(Path path) {
        try {
            getFileStatus(path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        if (sasClient != null) {
            sasClient.close();
        }
        sasCache.clear();
    }
} 