package com.jarvis.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in configuration for MCP servers (stdio and HTTP/SSE).
 * Disabled by default to protect system integrity until explicitly configured.
 */
@Configuration
@ConfigurationProperties(prefix = "jarvis.mcp")
public class McpConfiguration {

    private boolean enabled = false;
    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers != null ? servers : new LinkedHashMap<>();
    }

    public static class ServerConfig {
        private String type = "stdio"; // "stdio" or "http"
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private String url;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args != null ? args : new ArrayList<>();
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env != null ? env : new LinkedHashMap<>();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
