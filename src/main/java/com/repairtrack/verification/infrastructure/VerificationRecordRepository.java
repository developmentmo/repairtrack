package com.repairtrack.verification.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.verification.domain.VerificationRecord;

/** Append-only. */
public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, UUID> {

    List<VerificationRecord> findByRepairEventIdOrderByCreatedAtAsc(UUID repairEventId);
}
