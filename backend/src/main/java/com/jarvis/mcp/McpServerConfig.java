package com.jarvis.mcp;

import java.util.List;
import java.util.Map;

/**
 * Definition of an external MCP server connection.
 */
public record McpServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env,
        String type,
        boolean disabled,
        long timeoutMs
) {
    public McpServerConfig {
        args = args != null ? List.copyOf(args) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
        type = (type != null && !type.isBlank()) ? type : "stdio";
        if (timeoutMs <= 0) {
            timeoutMs = 10000L;
        }
    }

    public static McpServerConfig stdio(String name, String command, List<String> args, Map<String, String> env) {
        return new McpServerConfig(name, command, args, env, "stdio", false, 10000L);
    }
}
