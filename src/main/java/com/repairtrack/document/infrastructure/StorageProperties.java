package com.repairtrack.document.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Object storage settings ({@code repairtrack.storage.*}).
 *
 * @param endpoint         S3 API endpoint; empty means "AWS default for the region"
 * @param publicEndpoint   endpoint clients use to download (presigned URLs); empty means {@code endpoint}
 * @param accessKey        empty means "AWS default credentials chain" (e.g. an IAM role)
 * @param pathStyleAccess  {@code bucket} in the path instead of the host name (needed for Garage)
 */
@ConfigurationProperties(prefix = "repairtrack.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        Boolean pathStyleAccess,
        Duration presignedUrlTtl,
        DataSize maxFileSize
) {

    public StorageProperties {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("repairtrack.storage.region (S3_REGION) must be configured");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("repairtrack.storage.bucket (S3_BUCKET) must be configured");
        }
        pathStyleAccess = pathStyleAccess == null || pathStyleAccess;
        presignedUrlTtl = presignedUrlTtl == null ? Duration.ofMinutes(5) : presignedUrlTtl;
        maxFileSize = maxFileSize == null ? DataSize.ofMegabytes(20) : maxFileSize;
    }

    boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank();
    }

    @Override
    public String toString() {
        return "StorageProperties[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket + "]";
    }
}
