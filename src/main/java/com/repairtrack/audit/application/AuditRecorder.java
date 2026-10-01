package com.repairtrack.audit.application;

import static com.repairtrack.audit.application.AuditTrail.values;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.repairtrack.audit.domain.AuditAction;
import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.garage.GarageEvents;
import com.repairtrack.repair.RepairEvents;
import com.repairtrack.security.UserRegisteredEvent;
import com.repairtrack.vehicle.VehicleEvents;

/**
 * Turns module events into audit entries.
 * <p>
 * Deliberately plain, synchronous {@code @EventListener}s: they run in the publisher's
 * transaction, so a mutation and its audit entry commit or roll back together. Do not switch
 * these to {@code @TransactionalEventListener}/async listeners; audit entries could then be lost.
 * When an outbox is introduced, it is written here in the same way.
 */
@Component
class AuditRecorder {

    private final AuditTrail trail;

    AuditRecorder(AuditTrail trail) {
        this.trail = trail;
    }

    // ---------- security ----------

    @EventListener
    void on(UserRegisteredEvent event) {
        trail.record(AuditEntityType.USER, event.userId(), AuditAction.USER_REGISTERED, event.userId(),
                null, null, event.occurredAt());
    }

    // ---------- garage ----------

    @EventListener
    void on(GarageEvents.GarageRegistered event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_REGISTERED, event.registeredBy(),
                null, null, event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageVerificationStatusChanged event) {
        AuditAction action = switch (event.to()) {
            case VERIFIED -> AuditAction.GARAGE_VERIFIED;
            case SUSPENDED -> AuditAction.GARAGE_SUSPENDED;
            case UNVERIFIED -> AuditAction.GARAGE_VERIFICATION_REVOKED;
            case PENDING -> AuditAction.GARAGE_VERIFICATION_REQUESTED;
        };
        trail.record(AuditEntityType.GARAGE, event.garageId(), action, event.changedBy(),
                values("verificationStatus", event.from()),
                values("verificationStatus", event.to(), "note", event.note()), event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageMemberAdded event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_MEMBER_ADDED, event.addedBy(),
                null, values("userId", event.userId(), "role", event.role()), event.occurredAt());
    }

    @EventListener
    void on(GarageEvents.GarageMemberRemoved event) {
        trail.record(AuditEntityType.GARAGE, event.garageId(), AuditAction.GARAGE_MEMBER_REMOVED, event.removedBy(),
                values("userId", event.userId()), null, event.occurredAt());
    }

    // ---------- vehicle ----------

    @EventListener
    void on(VehicleEvents.VehicleRegistered event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_REGISTERED, event.registeredBy(),
                null, values("garageId", event.garageId()), event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleDetailsChanged event) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        event.changes().forEach(change -> {
            oldValues.put(change.field(), change.oldValue());
            newValues.put(change.field(), change.newValue());
        });
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_UPDATED, event.changedBy(),
                oldValues, newValues, event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleOwnershipStarted event) {
        AuditAction action = event.claimed() ? AuditAction.VEHICLE_CLAIMED : AuditAction.VEHICLE_OWNERSHIP_STARTED;
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), action, event.userId(),
                null, values("ownerId", event.userId(), "startDate", event.startDate()), event.occurredAt());
    }

    @EventListener
    void on(VehicleEvents.VehicleOwnershipEnded event) {
        trail.record(AuditEntityType.VEHICLE, event.vehicleId(), AuditAction.VEHICLE_OWNERSHIP_ENDED, event.userId(),
                values("ownerId", event.userId()), values("endDate", event.endDate()), event.occurredAt());
    }

    // ---------- repair ----------

    @EventListener
    void on(RepairEvents.RepairCreated event) {
        var s = event.snapshot();
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_CREATED, event.createdBy(),
                null, values("vehicleId", event.vehicleId(), "garageId", event.garageId(),
                        "eventType", s.eventType(), "eventDate", s.eventDate(), "mileage", s.mileage(),
                        "title", s.title(), "sourceType", s.sourceType(),
                        "verificationStatus", s.verificationStatus()),
                event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairCorrected event) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        Map<String, Object> newValues = new LinkedHashMap<>();
        event.corrections().forEach(c -> {
            oldValues.put(c.field(), c.oldValue());
            newValues.put(c.field(), c.newValue());
        });
        newValues.put("reason", event.reason());
        newValues.put("onBehalfOfGarageId", event.garageId());
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_CORRECTED,
                event.correctedBy(), oldValues, newValues, event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairVoided event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_VOIDED, event.voidedBy(),
                values("status", "ACTIVE"), values("status", "VOIDED", "reason", event.reason()),
                event.occurredAt());
    }

    @EventListener
    void on(RepairEvents.RepairPartAdded event) {
        trail.record(AuditEntityType.REPAIR_EVENT, event.repairId(), AuditAction.REPAIR_PART_ADDED, event.addedBy(),
                null, values("partId", event.partId(), "partNumber", event.partNumber(), "brand", event.brand(),
                        "description", event.description(), "quantity", event.quantity()),
                event.occurredAt());
    }
}
