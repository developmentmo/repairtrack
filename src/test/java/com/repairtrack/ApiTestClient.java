package com.repairtrack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/**
 * Minimal HTTP client for integration tests. Talks to the real server port, so the full stack
 * (security filter chain, MVC, error handling, JSON) is exercised exactly as a client sees it.
 */
public final class ApiTestClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;
    private final JsonMapper jsonMapper;

    public ApiTestClient(int port, JsonMapper jsonMapper) {
        this.baseUrl = "http://localhost:" + port;
        this.jsonMapper = jsonMapper;
    }

    public ApiResponse get(String path) {
        return get(path, null);
    }

    public ApiResponse get(String path, String bearerToken) {
        return send(request(path, bearerToken).GET(), bearerToken);
    }

    public ApiResponse delete(String path, String bearerToken) {
        return send(request(path, bearerToken).DELETE(), bearerToken);
    }

    public ApiResponse post(String path, Object body) {
        return post(path, body, null);
    }

    public ApiResponse post(String path, Object body, String bearerToken) {
        HttpRequest.Builder builder = request(path, bearerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        return send(builder, bearerToken);
    }

    private HttpRequest.Builder request(String path, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "application/json");
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return builder;
    }

    private ApiResponse send(HttpRequest.Builder builder, String bearerToken) {
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String raw = response.body();
            JsonNode body = raw == null || raw.isBlank() ? MissingNode.getInstance() : jsonMapper.readTree(raw);
            return new ApiResponse(response.statusCode(), response.headers(), body);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    public record ApiResponse(int status, HttpHeaders headers, JsonNode body) {

        /** Text value of a top-level field, or null when absent. */
        public String field(String name) {
            JsonNode node = body.get(name);
            return node == null || node.isNull() ? null : node.asString();
        }
    }
}
