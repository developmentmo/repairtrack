package com.repairtrack.audit.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One immutable audit entry. The table additionally rejects UPDATE and DELETE with a trigger.
 * Values hold IDs and business data only; never credentials, emails or names.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Assigned by the database (identity column); gives a strict order within one instant. */
    @Column(name = "sequence_number", insertable = false, updatable = false)
    private Long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 50)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 60)
    private AuditAction action;

    /** Null for system-initiated changes. */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", updatable = false)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected AuditEvent() {
        // for JPA
    }

    public static AuditEvent of(AuditEntityType entityType, UUID entityId, AuditAction action, UUID actorId,
                                String oldValueJson, String newValueJson, Instant createdAt) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.entityType = Objects.requireNonNull(entityType, "entityType");
        event.entityId = Objects.requireNonNull(entityId, "entityId");
        event.action = Objects.requireNonNull(action, "action");
        event.actorId = actorId;
        event.oldValue = oldValueJson;
        event.newValue = newValueJson;
        event.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return event;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        isNew = false;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
