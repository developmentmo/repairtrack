package com.repairtrack.audit.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.audit.domain.AuditEvent;

/** Append-only: only save and read. Never add update or delete queries here. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByEntityTypeAndEntityIdOrderBySequenceNumberAsc(AuditEntityType entityType, UUID entityId);
}
