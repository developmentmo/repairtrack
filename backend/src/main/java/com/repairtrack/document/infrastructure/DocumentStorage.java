package com.repairtrack.document.infrastructure;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;

/**
 * Port to private object storage. Keeps the AWS SDK out of the application layer and makes the
 * document service unit-testable; the only implementation is {@link S3DocumentStorage}.
 */
public interface DocumentStorage {

    /** Stores exactly {@code size} bytes from {@code content}. */
    void put(String key, InputStream content, long size, String contentType);

    /** Streams a stored object, or empty if it does not exist. The caller closes the stream. */
    Optional<InputStream> open(String key);

    /** Time-limited download URL for a private object, served as an attachment with the given name. */
    URL presignDownload(String key, String fileName, String contentType, Duration ttl);

    /** Best-effort removal; used only to compensate a failed transaction, never to delete history. */
    void deleteQuietly(String key);

    /** Throws when the bucket cannot be reached (health check). */
    void checkAvailable();
}
