package com.repairtrack.security.infrastructure;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser origins that may call the API, e.g. the Flutter web app ({@code https://app.repairtrack.nl}).
 * Empty (the default) means no cross-origin access; mobile apps are not affected by CORS.
 */
@ConfigurationProperties(prefix = "repairtrack.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException("repairtrack.cors.allowed-origins must list explicit origins, not '*'");
        }
    }
}
