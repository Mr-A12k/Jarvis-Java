package com.jarvis.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Transport boundary for JSON-RPC 2.0 communication with an MCP server.
 */
public interface McpTransport extends AutoCloseable {

    /**
     * Unique identifier for the server managed by this transport.
     */
    String getServerName();

    /**
     * Starts the transport connection (spawns process or opens socket/stream).
     */
    void start() throws IOException;

    /**
     * Sends a JSON-RPC request and returns a future for the result.
     */
    CompletableFuture<JsonNode> sendRequest(String method, Object params);

    /**
     * Sends a one-way JSON-RPC notification.
     */
    void sendNotification(String method, Object params);

    /**
     * Indicates whether the transport is currently connected and active.
     */
    boolean isConnected();

    /**
     * Gracefully terminates the transport and releases underlying resources.
     */
    @Override
    void close();
}
