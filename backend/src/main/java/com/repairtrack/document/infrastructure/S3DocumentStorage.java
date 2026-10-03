package com.repairtrack.document.infrastructure;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import com.repairtrack.document.domain.FileNames;

@Component
class S3DocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentStorage.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    S3DocumentStorage(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .build(),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public Optional<InputStream> open(String key) {
        try {
            return Optional.of(s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        }
    }

    @Override
    public URL presignDownload(String key, String fileName, String contentType, Duration ttl) {
        String disposition = "attachment; filename=\"" + FileNames.asciiFallback(fileName) + "\"; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition(disposition)
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(request)
                        .build())
                .url();
    }

    @Override
    public void deleteQuietly(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ex) {
            log.warn("Could not remove orphaned object {} after a failed upload transaction", key, ex);
        }
    }

    @Override
    public void checkAvailable() {
        s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    }
}
