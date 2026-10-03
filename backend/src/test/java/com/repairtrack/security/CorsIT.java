package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.repairtrack.IntegrationTest;

/** The web app's origin may call the API from a browser; other origins may not. */
@IntegrationTest
class CorsIT {

    private static final String WEB_APP = "https://app.repairtrack.example";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void preflightFromTheWebAppIsAllowed() throws Exception {
        HttpResponse<String> response = preflight(WEB_APP, "POST");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(WEB_APP);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("POST");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void publicReportAnswersTheWebAppWithCorsHeaders() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/v1/public/vehicles/unknown-token"))
                .header("Origin", WEB_APP)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(WEB_APP);
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        HttpResponse<String> response = preflight("https://evil.example", "POST");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<String> preflight(String origin, String method) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
