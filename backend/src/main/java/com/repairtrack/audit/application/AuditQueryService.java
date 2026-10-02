package com.repairtrack.audit.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.audit.domain.AuditEvent;
import com.repairtrack.audit.infrastructure.AuditEventRepository;
import com.repairtrack.common.error.ApplicationException;
import com.repairtrack.common.error.ErrorCategory;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;

/** Audit trail lookups; system admins only (it contains actor IDs across users). */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> forEntity(AuthenticatedUser actor, AuditEntityType entityType, UUID entityId) {
        if (!actor.hasRole(Role.SYSTEM_ADMIN)) {
            throw new AuditAccessDeniedException();
        }
        return repository.findByEntityTypeAndEntityIdOrderBySequenceNumberAsc(entityType, entityId);
    }

    public static class AuditAccessDeniedException extends ApplicationException {

        public AuditAccessDeniedException() {
            super(ErrorCategory.FORBIDDEN, "SYSTEM_ADMIN_REQUIRED", "Only RepairTrack administrators can read the audit trail.");
        }
    }
}
