package com.repairtrack.document.application;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentEvents;
import com.repairtrack.document.DocumentType;
import com.repairtrack.document.application.DocumentViews.DocumentView;
import com.repairtrack.document.application.DocumentViews.DownloadView;
import com.repairtrack.document.application.DocumentViews.IntegrityView;
import com.repairtrack.document.domain.DetectedFileType;
import com.repairtrack.document.domain.Document;
import com.repairtrack.document.domain.EmptyFileException;
import com.repairtrack.document.domain.FileNames;
import com.repairtrack.document.domain.FileTooLargeException;
import com.repairtrack.document.domain.UnsupportedFileTypeException;
import com.repairtrack.document.infrastructure.DocumentRepository;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.StorageProperties;
import com.repairtrack.repair.RepairDocumentSupport;
import com.repairtrack.repair.RepairDocumentSupport.AttachmentTarget;
import com.repairtrack.security.AuthenticatedUser;

/**
 * Documents attached to history records.
 * <p>
 * Upload: authorize &rarr; check size &rarr; detect the type from the content &rarr; stream to object
 * storage while computing SHA-256 &rarr; store metadata &rarr; possibly raise the record to DOCUMENTED.
 * The hash is computed by the backend over the exact bytes stored, so it can later prove whether
 * the stored file was changed.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final StorageProperties properties;
    private final RepairDocumentSupport repairSupport;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public DocumentService(DocumentRepository documents, DocumentStorage storage, StorageProperties properties,
                           RepairDocumentSupport repairSupport, ApplicationEventPublisher events,
                           BusinessCalendar calendar) {
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
        this.repairSupport = repairSupport;
        this.events = events;
        this.calendar = calendar;
    }

    @Transactional
    public DocumentView upload(AuthenticatedUser actor, UUID repairId, DocumentType documentType, IncomingFile file) {
        AttachmentTarget target = repairSupport.requireCanAttachDocument(actor, repairId);
        if (file.size() <= 0) {
            throw new EmptyFileException();
        }
        if (file.size() > properties.maxFileSize().toBytes()) {
            throw new FileTooLargeException();
        }

        BufferedInputStream content = new BufferedInputStream(file.content());
        DetectedFileType type = detectType(content);

        UUID documentId = UUID.randomUUID();
        String key = Document.storageKeyFor(repairId, documentId);
        MessageDigest digest = sha256();
        storage.put(key, new DigestInputStream(content, digest), file.size(), type.mimeType());
        removeObjectIfTransactionRollsBack(key);
        String sha256 = HexFormat.of().formatHex(digest.digest());

        Instant now = calendar.now();
        Document document = Document.create(documentId, repairId, target.vehicleId(), documentType,
                FileNames.sanitize(file.fileName(), type), key, type.mimeType(), file.size(), sha256, actor.id(), now);
        documents.save(document);

        boolean raised = documentType.isEvidence() && repairSupport.applyDocumentEvidence(actor, repairId, documentId);
        events.publishEvent(new DocumentEvents.DocumentUploaded(documentId, repairId, target.vehicleId(),
                documentType, type.mimeType(), file.size(), sha256, actor.id(), now));
        return DocumentView.of(document, raised);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> listForRepair(AuthenticatedUser actor, UUID repairId) {
        repairSupport.requireCanViewRepair(actor, repairId);
        return documents.findByRepairEventIdOrderByUploadedAtAsc(repairId).stream()
                .map(document -> DocumentView.of(document, false))
                .toList();
    }

    /** Metadata plus a short-lived download URL. The bucket itself is private. */
    @Transactional(readOnly = true)
    public DownloadView download(AuthenticatedUser actor, UUID documentId) {
        Document document = visibleDocument(actor, documentId);
        Instant expiresAt = calendar.now().plus(properties.presignedUrlTtl());
        return new DownloadView(DocumentView.of(document, false),
                storage.presignDownload(document.getStorageKey(), document.getFileName(), document.getMimeType(),
                        properties.presignedUrlTtl()),
                expiresAt);
    }

    /** Re-reads the stored object and compares its SHA-256 with the hash taken at upload. */
    @Transactional(readOnly = true)
    public IntegrityView checkIntegrity(AuthenticatedUser actor, UUID documentId) {
        Document document = visibleDocument(actor, documentId);
        Optional<InputStream> stored = storage.open(document.getStorageKey());
        String actual = stored.map(DocumentService::sha256Of).orElse(null);
        boolean intact = document.getSha256().equals(actual);
        if (!intact) {
            log.warn("Integrity check failed for document {} (stored object {})", documentId,
                    actual == null ? "missing" : "changed");
        }
        return new IntegrityView(documentId, document.getSha256(), actual, intact, calendar.now());
    }

    private Document visibleDocument(AuthenticatedUser actor, UUID documentId) {
        Document document = documents.findById(documentId).orElseThrow(DocumentNotFoundException::new);
        repairSupport.requireCanViewRepair(actor, document.getRepairEventId());
        return document;
    }

    private static DetectedFileType detectType(BufferedInputStream content) {
        try {
            content.mark(DetectedFileType.HEADER_LENGTH);
            byte[] header = content.readNBytes(DetectedFileType.HEADER_LENGTH);
            content.reset();
            return DetectedFileType.detect(header).orElseThrow(UnsupportedFileTypeException::new);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Never leave an object without metadata behind when the database transaction fails. */
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

    private static String sha256Of(InputStream stream) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(stream, digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
