package com.repairtrack.vehicle.infrastructure.registry;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lookup of public vehicle data ({@code repairtrack.vehicle-registry.*}).
 *
 * @param mode           {@code rdw} (RDW Open Data) or {@code disabled} (every lookup answers 503)
 * @param baseUrl        RDW Open Data host (Socrata), {@code https://opendata.rdw.nl}
 * @param appToken       optional Socrata app token (higher rate limits at the RDW); not a secret, but kept out of
 *                       the code anyway
 * @param connectTimeout connect timeout
 * @param readTimeout    time to wait for each answer; a slow registry must not hold up the form
 * @param cacheTtl       how long answers (also "unknown plate") are reused
 * @param cacheSize      maximum number of cached plates
 */
@ConfigurationProperties(prefix = "repairtrack.vehicle-registry")
public record VehicleRegistryProperties(String mode, String baseUrl, String appToken, Duration connectTimeout,
                                        Duration readTimeout, Duration cacheTtl, Integer cacheSize) {

    public VehicleRegistryProperties {
        mode = mode == null || mode.isBlank() ? "rdw" : mode.trim().toLowerCase();
        if (!mode.equals("rdw") && !mode.equals("disabled")) {
            throw new IllegalStateException("repairtrack.vehicle-registry.mode must be 'rdw' or 'disabled'");
        }
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://opendata.rdw.nl" : stripTrailingSlash(baseUrl.trim());
        appToken = appToken == null || appToken.isBlank() ? null : appToken.trim();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(4) : readTimeout;
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        cacheSize = cacheSize == null ? 10_000 : cacheSize;
    }

    public boolean enabled() {
        return mode.equals("rdw");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
