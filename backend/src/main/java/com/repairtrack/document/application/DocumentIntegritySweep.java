package com.repairtrack.document.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentEvents;
import com.repairtrack.document.domain.Document;
import com.repairtrack.document.infrastructure.DocumentRepository;
import com.repairtrack.document.infrastructure.DocumentStorage;

/**
 * Periodically re-reads every stored document and compares its SHA-256 with the hash taken at upload.
 * A missing or changed object is logged as an error and audited ({@code DOCUMENT_INTEGRITY_FAILED}); it is never
 * "repaired" automatically, because the stored hash is the evidence. Runs page by page without one long
 * transaction; idempotent, so several instances may run it.
 */
@Component
public class DocumentIntegritySweep {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntegritySweep.class);
    private static final int PAGE_SIZE = 100;

    public record Result(int checked, int failed) {
    }

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    public DocumentIntegritySweep(DocumentRepository documents, DocumentStorage storage,
                                  ApplicationEventPublisher events, BusinessCalendar calendar,
                                  PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.storage = storage;
        this.events = events;
        this.calendar = calendar;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${repairtrack.storage.integrity-sweep-cron:0 0 4 * * SUN}",
            zone = "${repairtrack.business-time-zone:Europe/Amsterdam}")
    public Result run() {
        int checked = 0;
        int failed = 0;
        PageRequest page = PageRequest.of(0, PAGE_SIZE, Sort.by("uploadedAt", "id"));
        Page<Document> batch;
        do {
            batch = documents.findAll(page);
            for (Document document : batch) {
                checked++;
                Optional<String> problem = check(document);
                if (problem.isPresent()) {
                    failed++;
                    report(document, problem.get());
                }
            }
            page = page.next();
        } while (batch.hasNext());
        if (failed > 0) {
            log.error("Document integrity sweep: {} of {} document(s) missing or changed", failed, checked);
        } else {
            log.info("Document integrity sweep: {} document(s) intact", checked);
        }
        return new Result(checked, failed);
    }

    /** @return {@code MISSING}, {@code CHANGED} or {@code UNREADABLE}; empty when intact */
    private Optional<String> check(Document document) {
        try {
            Optional<InputStream> stored = storage.open(document.getStorageKey());
            if (stored.isEmpty()) {
                return Optional.of("MISSING");
            }
            return document.getSha256().equals(sha256Of(stored.get())) ? Optional.empty() : Optional.of("CHANGED");
        } catch (RuntimeException e) {
            log.warn("Document {} could not be read during the integrity sweep: {}", document.getId(), e.getMessage());
            return Optional.of("UNREADABLE");
        }
    }

    private void report(Document document, String reason) {
        log.error("Integrity check failed for document {} (repair {}): {}", document.getId(),
                document.getRepairEventId(), reason);
        transaction.executeWithoutResult(status -> events.publishEvent(new DocumentEvents.IntegrityCheckFailed(
                document.getId(), document.getRepairEventId(), reason, calendar.now())));
    }

    private static String sha256Of(InputStream stream) {
        try (InputStream in = new DigestInputStream(stream, sha256())) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
