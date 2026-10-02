package com.repairtrack.sharing.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param publicBaseUrl   base of the human-facing share URL, e.g. {@code https://repairtrack.nl};
 *                        links look like {@code {publicBaseUrl}/v/{token}}
 * @param defaultValidity validity when the owner does not choose one
 */
@ConfigurationProperties(prefix = "repairtrack.sharing")
public record SharingProperties(String publicBaseUrl, Duration defaultValidity) {

    public SharingProperties {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalStateException("repairtrack.sharing.public-base-url must be configured");
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        defaultValidity = defaultValidity == null ? Duration.ofDays(30) : defaultValidity;
    }
}
