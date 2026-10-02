package com.repairtrack.verification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.verification.domain.VerificationRecord;
import com.repairtrack.verification.infrastructure.VerificationRecordRepository;

/**
 * Persistent history of provenance changes after creation (the initial provenance is on the
 * record itself). Kept separate from {@link VerificationService}, which stays a pure policy.
 */
@Service
public class VerificationLog {

    private final VerificationRecordRepository records;

    public VerificationLog(VerificationRecordRepository records) {
        this.records = records;
    }

    @Transactional
    public VerificationChange record(UUID repairEventId, Provenance from, Provenance to, VerificationMethod method,
                                     UUID evidenceId, UUID changedBy, Instant now) {
        return records.save(VerificationRecord.of(repairEventId, from, to, method, evidenceId, changedBy, now))
                .toChange();
    }

    @Transactional(readOnly = true)
    public List<VerificationChange> history(UUID repairEventId) {
        return records.findByRepairEventIdOrderByCreatedAtAsc(repairEventId).stream()
                .map(VerificationRecord::toChange)
                .toList();
    }
}
