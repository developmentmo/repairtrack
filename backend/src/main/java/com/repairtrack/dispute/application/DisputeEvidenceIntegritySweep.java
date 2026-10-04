package com.repairtrack.dispute.application;

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
import com.repairtrack.dispute.DisputeEvents;
import com.repairtrack.dispute.domain.DisputeEvidence;
import com.repairtrack.dispute.infrastructure.DisputeEvidenceRepository;
import com.repairtrack.document.EvidenceFiles;

/**
 * Weekly re-check of every dispute evidence file against the SHA-256 taken at upload, on the same schedule as the
 * document sweep. A missing or changed file is logged and audited ({@code DISPUTE_EVIDENCE_INTEGRITY_FAILED} on the
 * dispute); nothing is repaired. Page by page without one long transaction; idempotent.
 */
@Component
public class DisputeEvidenceIntegritySweep {

    private static final Logger log = LoggerFactory.getLogger(DisputeEvidenceIntegritySweep.class);
    private static final int PAGE_SIZE = 100;

    public record Result(int checked, int failed) {
    }

    private final DisputeEvidenceRepository evidence;
    private final EvidenceFiles files;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;
    private final TransactionTemplate transaction;

    public DisputeEvidenceIntegritySweep(DisputeEvidenceRepository evidence, EvidenceFiles files,
                                         ApplicationEventPublisher events, BusinessCalendar calendar,
                                         PlatformTransactionManager transactionManager) {
        this.evidence = evidence;
        this.files = files;
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
        Page<DisputeEvidence> batch;
        do {
            batch = evidence.findAll(page);
            for (DisputeEvidence file : batch) {
                checked++;
                Optional<String> problem = files.verify(file.getStorageKey(), file.getSha256());
                if (problem.isPresent()) {
                    failed++;
                    report(file, problem.get());
                }
            }
            page = page.next();
        } while (batch.hasNext());
        if (failed > 0) {
            log.error("Dispute evidence integrity sweep: {} of {} file(s) missing or changed", failed, checked);
        } else {
            log.info("Dispute evidence integrity sweep: {} file(s) intact", checked);
        }
        return new Result(checked, failed);
    }

    private void report(DisputeEvidence file, String reason) {
        log.error("Integrity check failed for evidence {} (dispute {}): {}", file.getId(), file.getDisputeId(),
                reason);
        transaction.executeWithoutResult(status -> events.publishEvent(new DisputeEvents.EvidenceIntegrityCheckFailed(
                file.getDisputeId(), file.getId(), reason, calendar.now())));
    }
}
