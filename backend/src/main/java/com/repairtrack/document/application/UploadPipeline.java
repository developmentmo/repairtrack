package com.repairtrack.document.application;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.repairtrack.document.domain.DetectedFileType;
import com.repairtrack.document.domain.EmptyFileException;
import com.repairtrack.document.domain.FileTooLargeException;
import com.repairtrack.document.domain.UnsupportedFileTypeException;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.MalwareScanner;
import com.repairtrack.document.infrastructure.StorageProperties;

/**
 * The checks every stored file goes through, shared by history documents and dispute evidence:
 * size &rarr; type from the content &rarr; malware scan &rarr; stream to storage while computing SHA-256.
 * Not a bean: each user builds one from the same infrastructure.
 */
public final class UploadPipeline {

    private final DocumentStorage storage;
    private final MalwareScanner scanner;
    private final StorageProperties properties;

    public UploadPipeline(DocumentStorage storage, MalwareScanner scanner, StorageProperties properties) {
        this.storage = storage;
        this.scanner = scanner;
        this.properties = properties;
    }

    /** Size limits and the real file type (magic bytes, never the name or the client's content type). */
    public DetectedFileType check(IncomingFile file) {
        return check(file, DetectedFileType.DOCUMENTS, UnsupportedFileTypeException::new);
    }

    /** As {@link #check(IncomingFile)}, for a different set of accepted types. */
    public DetectedFileType check(IncomingFile file, Set<DetectedFileType> accepted,
                                  Supplier<UnsupportedFileTypeException> unsupported) {
        if (file.size() <= 0) {
            throw new EmptyFileException();
        }
        if (file.size() > properties.maxFileSize().toBytes()) {
            throw new FileTooLargeException();
        }
        try (InputStream content = file.content().open()) {
            byte[] header = content.readNBytes(DetectedFileType.HEADER_LENGTH);
            return DetectedFileType.detect(header).filter(accepted::contains).orElseThrow(unsupported);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Fails closed: no verdict throws {@code MalwareScannerUnavailableException}. */
    public MalwareScanner.ScanResult scan(IncomingFile file) {
        try (InputStream content = new BufferedInputStream(file.content().open())) {
            return scanner.scan(content);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Stores the file under {@code key} and returns the SHA-256 of exactly the stored bytes. If the surrounding
     * transaction rolls back, the object is removed again, so no object is left without metadata.
     */
    public String store(String key, IncomingFile file, DetectedFileType type) {
        MessageDigest digest = sha256();
        try (InputStream content = file.content().open()) {
            storage.put(key, new DigestInputStream(content, digest), file.size(), type.mimeType());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        removeObjectIfTransactionRollsBack(key);
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Re-reads a stored object and compares its SHA-256 with {@code expectedSha256}.
     *
     * @return {@code MISSING}, {@code CHANGED} or {@code UNREADABLE}; empty when intact
     */
    public Optional<String> verify(String key, String expectedSha256) {
        try {
            Optional<InputStream> stored = storage.open(key);
            if (stored.isEmpty()) {
                return Optional.of("MISSING");
            }
            MessageDigest digest = sha256();
            try (InputStream in = new DigestInputStream(stored.get(), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            return expectedSha256.equals(actual) ? Optional.empty() : Optional.of("CHANGED");
        } catch (IOException | RuntimeException e) {
            return Optional.of("UNREADABLE");
        }
    }

    private void removeObjectIfTransactionRollsBack(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.deleteQuietly(key);
                    }
                }
            });
        }
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
