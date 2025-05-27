package com.spark.hdfs.sas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Output stream implementation that writes to a SAS URL
 */
public class SASOutputStream extends OutputStream {
    
    private static final Logger LOG = LoggerFactory.getLogger(SASOutputStream.class);
    
    private final String sasUrl;
    private final int bufferSize;
    private HttpURLConnection connection;
    private OutputStream outputStream;
    private boolean closed = false;
    private long position = 0;
    
    public SASOutputStream(String sasUrl, int bufferSize) throws IOException {
        this.sasUrl = sasUrl;
        this.bufferSize = bufferSize;
        openConnection();
    }
    
    private void openConnection() throws IOException {
        LOG.debug("Opening connection to SAS URL for writing");
        
        URL url = new URL(sasUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000); // 30 seconds
        connection.setReadTimeout(60000); // 60 seconds
        
        // Set content type for blob storage
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("x-ms-blob-type", "BlockBlob");
        
        outputStream = connection.getOutputStream();
    }
    
    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        outputStream.write(b);
        position++;
    }
    
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }
    
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        outputStream.write(b, off, len);
        position += len;
    }
    
    @Override
    public void flush() throws IOException {
        ensureOpen();
        outputStream.flush();
    }
    
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            
            if (outputStream != null) {
                try {
                    outputStream.close();
                    
                    // Check response code after closing the output stream
                    int responseCode = connection.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        throw new IOException("Failed to write to SAS URL: HTTP " + responseCode + " - " + connection.getResponseMessage());
                    }
                    
                    LOG.debug("Successfully wrote {} bytes to SAS URL", position);
                } catch (IOException e) {
                    LOG.error("Error closing output stream", e);
                    throw e;
                }
                outputStream = null;
            }
            
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
            
            LOG.debug("Closed SAS output stream");
        }
    }
    
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
    }
    
    /**
     * Get the current position in the stream
     */
    public long getPos() {
        return position;
    }
} 