package com.jarvis.smarthome;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Home Assistant REST API client.
 * Connects JARVIS to Home Assistant instances running on the local network
 * (e.g. Raspberry Pi, Home Assistant Green, Docker, or Supervised VM).
 */
@Component
public class HomeAssistantClient {

    private static final Logger log = LoggerFactory.getLogger(HomeAssistantClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper json;

    public HomeAssistantClient() {
        this(new ObjectMapper());
    }

    public HomeAssistantClient(ObjectMapper json) {
        this.json = json != null ? json : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Verifies if Home Assistant instance is reachable with the provided token.
     */
    public boolean testConnection(String baseUrl, String token) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        try {
            String url = sanitizeUrl(baseUrl) + "/api/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("API running");
        } catch (Exception e) {
            log.warn("Home Assistant connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fetches current states of all entities registered in Home Assistant.
     */
    public List<Map<String, Object>> fetchStates(String baseUrl, String token) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String url = sanitizeUrl(baseUrl) + "/api/states";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return json.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
            } else {
                log.warn("Home Assistant states fetch returned HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("Home Assistant fetchStates error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Calls a service in Home Assistant (e.g. light/turn_on, switch/turn_off, scene/turn_on).
     */
    public boolean callService(String baseUrl, String token, String domain, String service, Map<String, Object> payload) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        try {
            String url = sanitizeUrl(baseUrl) + "/api/services/" + domain + "/" + service;
            String jsonBody = json.writeValueAsString(payload != null ? payload : Map.of());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(4))
                    .header("Authorization", "Bearer " + token.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("Home Assistant service call {}.{} returned HTTP {}: {}", domain, service, response.statusCode(), response.body());
            }
            return ok;
        } catch (Exception e) {
            log.warn("Home Assistant callService failed for {}.{}: {}", domain, service, e.getMessage());
            return false;
        }
    }

    private String sanitizeUrl(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
