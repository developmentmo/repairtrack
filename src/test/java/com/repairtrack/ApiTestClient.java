package com.repairtrack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

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

    /** multipart/form-data with simple text fields and one file part named {@code file}. */
    public ApiResponse postFile(String path, Map<String, String> fields, String fileName, String contentType,
                                byte[] content, String bearerToken) {
        String boundary = "----RepairTrackTest" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        fields.forEach((name, value) -> write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n"));
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n");
        body.writeBytes(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest.Builder builder = request(path, bearerToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        return send(builder, bearerToken);
    }

    /** Plain GET of an absolute URL (e.g. a presigned download link), returning the raw bytes. */
    public HttpResponse<byte[]> download(String absoluteUrl) {
        try {
            return httpClient.send(HttpRequest.newBuilder(URI.create(absoluteUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    public ApiResponse put(String path, Object body, String bearerToken) {
        HttpRequest.Builder builder = request(path, bearerToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        return send(builder, bearerToken);
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
