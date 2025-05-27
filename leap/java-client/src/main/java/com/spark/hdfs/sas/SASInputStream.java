package com.spark.hdfs.sas;

import org.apache.hadoop.fs.FSInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Input stream implementation that reads from a SAS URL
 */
public class SASInputStream extends FSInputStream {
    
    private static final Logger LOG = LoggerFactory.getLogger(SASInputStream.class);
    
    private final String sasUrl;
    private final int bufferSize;
    private HttpURLConnection connection;
    private InputStream inputStream;
    private long position = 0;
    private boolean closed = false;
    
    public SASInputStream(String sasUrl, int bufferSize) {
        this.sasUrl = sasUrl;
        this.bufferSize = bufferSize;
    }
    
    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        
        if (inputStream == null) {
            openConnection();
        }
    }
    
    private void openConnection() throws IOException {
        LOG.debug("Opening connection to SAS URL");
        
        URL url = new URL(sasUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000); // 30 seconds
        connection.setReadTimeout(60000); // 60 seconds
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to open SAS URL: HTTP " + responseCode + " - " + connection.getResponseMessage());
        }
        
        inputStream = connection.getInputStream();
    }
    
    @Override
    public int read() throws IOException {
        ensureOpen();
        
        int data = inputStream.read();
        if (data != -1) {
            position++;
        }
        return data;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        
        int bytesRead = inputStream.read(b, off, len);
        if (bytesRead > 0) {
            position += bytesRead;
        }
        return bytesRead;
    }
    
    @Override
    public void seek(long pos) throws IOException {
        if (pos < 0) {
            throw new IOException("Negative seek position: " + pos);
        }
        
        if (pos == position) {
            return; // Already at the desired position
        }
        
        // Close current connection and reopen with range header
        close();
        
        LOG.debug("Seeking to position: {}", pos);
        
        URL url = new URL(sasUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Range", "bytes=" + pos + "-");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to seek in SAS URL: HTTP " + responseCode + " - " + connection.getResponseMessage());
        }
        
        inputStream = connection.getInputStream();
        position = pos;
        closed = false;
    }
    
    @Override
    public long getPos() throws IOException {
        return position;
    }
    
    @Override
    public boolean seekToNewSource(long targetPos) throws IOException {
        // For HTTP streams, we can't seek to a new source
        return false;
    }
    
    @Override
    public int available() throws IOException {
        ensureOpen();
        return inputStream.available();
    }
    
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    LOG.warn("Error closing input stream", e);
                }
                inputStream = null;
            }
            
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
            
            LOG.debug("Closed SAS input stream");
        }
    }
    
    @Override
    public void mark(int readlimit) {
        // Mark is not supported for HTTP streams
    }
    
    @Override
    public void reset() throws IOException {
        throw new IOException("Mark/reset not supported");
    }
    
    @Override
    public boolean markSupported() {
        return false;
    }
} 