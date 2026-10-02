package com.repairtrack.repair.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageWorkPermit;
import com.repairtrack.mileage.MileageAnomaly;
import com.repairtrack.mileage.MileageService;
import com.repairtrack.repair.RepairEvents;
import com.repairtrack.repair.application.Commands.AddPart;
import com.repairtrack.repair.application.Commands.CreateRepair;
import com.repairtrack.repair.application.Views.RepairResult;
import com.repairtrack.repair.application.Views.RepairView;
import com.repairtrack.repair.domain.RepairAlreadyVoidedException;
import com.repairtrack.repair.domain.RepairChanges;
import com.repairtrack.repair.domain.RepairCorrection;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.domain.RepairPart;
import com.repairtrack.repair.infrastructure.RepairCorrectionRepository;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.repair.infrastructure.RepairPartRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.RecordingContext;
import com.repairtrack.verification.VerificationService;

/**
 * Mutations of the vehicle history. Each public method is one transaction that atomically writes
 * the repair data, the mileage record(s) and, via synchronously handled events, the audit trail.
 */
@Service
public class RepairService {

    private final RepairEventRepository repairs;
    private final RepairPartRepository parts;
    private final RepairCorrectionRepository corrections;
    private final RepairAccessPolicy access;
    private final RepairViewAssembler assembler;
    private final VehicleAccessService vehicleAccess;
    private final GarageAccessService garageAccess;
    private final VerificationService verification;
    private final MileageService mileage;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public RepairService(RepairEventRepository repairs, RepairPartRepository parts,
                         RepairCorrectionRepository corrections, RepairAccessPolicy access,
                         RepairViewAssembler assembler, VehicleAccessService vehicleAccess,
                         GarageAccessService garageAccess, VerificationService verification,
                         MileageService mileage, ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.repairs = repairs;
        this.parts = parts;
        this.corrections = corrections;
        this.access = access;
        this.assembler = assembler;
        this.vehicleAccess = vehicleAccess;
        this.garageAccess = garageAccess;
        this.verification = verification;
        this.mileage = mileage;
        this.events = events;
        this.calendar = calendar;
    }

    /**
     * Records maintenance/repair. Source type and verification status are derived here from WHO is
     * recording (established by authorization), never taken from the request.
     */
    @Transactional
    public RepairResult create(AuthenticatedUser actor, UUID vehicleId, CreateRepair command) {
        vehicleAccess.requireExists(vehicleId);
        RecordingContext context;
        if (command.garageId() != null) {
            GarageWorkPermit permit = garageAccess.validateCanRecordWork(actor, command.garageId());
            context = new RecordingContext.ByGarage(permit.garageVerified());
        } else {
            vehicleAccess.requireActiveOwner(actor, vehicleId);
            context = new RecordingContext.ByOwner();
        }
        Provenance provenance = verification.determine(context);

        Instant now = calendar.now();
        LocalDate today = calendar.today();
        RepairEvent event = RepairEvent.record(vehicleId, command.garageId(), actor.id(), command.eventType(),
                provenance, command.eventDate(), command.mileage(), command.title(), command.description(), now, today);
        repairs.save(event);
        List<RepairPart> savedParts = new ArrayList<>();
        for (AddPart part : command.parts()) {
            savedParts.add(parts.save(RepairPart.add(event.getId(), part.partNumber(), part.brand(),
                    part.description(), part.quantity(), actor.id(), now)));
        }
        repairs.flush();

        List<MileageAnomaly> warnings = mileage.record(vehicleId, event.getMileage(), event.getEventDate(),
                event.getSourceType(), event.getId(), now);
        events.publishEvent(new RepairEvents.RepairCreated(event.getId(), vehicleId, event.getGarageId(),
                actor.id(), event.snapshot(), now));
        publishPartsAdded(event, savedParts, actor, now);
        return new RepairResult(assembler.toView(event), warnings);
    }

    /** Corrects fields of a record; the original values remain visible as correction history. */
    @Transactional
    public RepairResult correct(AuthenticatedUser actor, UUID repairId, RepairChanges changes, String reason) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        UUID onBehalfOfGarage = access.requireCanModify(actor, event);
        Instant now = calendar.now();
        int mileageBefore = event.getMileage();
        LocalDate dateBefore = event.getEventDate();

        List<RepairCorrection> applied = event.correct(changes, reason, actor.id(), onBehalfOfGarage, now,
                calendar.today());
        corrections.saveAll(applied);

        List<MileageAnomaly> warnings = List.of();
        if (event.getMileage() != mileageBefore || !event.getEventDate().equals(dateBefore)) {
            // never edit a mileage record in place: void the old reading, register the corrected one
            mileage.voidForSourceEvent(event.getId(), now);
            warnings = mileage.record(event.getVehicleId(), event.getMileage(), event.getEventDate(),
                    event.getSourceType(), event.getId(), now);
        }
        events.publishEvent(new RepairEvents.RepairCorrected(event.getId(), event.getVehicleId(), actor.id(),
                onBehalfOfGarage,
                applied.stream().map(c -> new RepairEvents.FieldCorrection(c.getField().name(), c.getOldValue(),
                        c.getNewValue())).toList(),
                applied.getFirst().getReason(), now));
        return new RepairResult(assembler.toView(event), warnings);
    }

    /** Declares a record invalid. It stays in the history, marked VOIDED with the reason. */
    @Transactional
    public RepairView voidRepair(AuthenticatedUser actor, UUID repairId, String reason) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanVoid(actor, event);
        Instant now = calendar.now();
        event.voidEvent(reason, actor.id(), now);
        mileage.voidForSourceEvent(event.getId(), now);
        events.publishEvent(new RepairEvents.RepairVoided(event.getId(), event.getVehicleId(), actor.id(),
                event.getVoidReason(), now));
        return assembler.toView(event);
    }

    @Transactional
    public RepairView addParts(AuthenticatedUser actor, UUID repairId, List<AddPart> newParts) {
        RepairEvent event = repairs.findById(repairId).orElseThrow(RepairNotFoundException::new);
        access.requireCanModify(actor, event);
        if (event.isVoided()) {
            throw new RepairAlreadyVoidedException();
        }
        Instant now = calendar.now();
        List<RepairPart> saved = new ArrayList<>();
        for (AddPart part : newParts) {
            saved.add(parts.save(RepairPart.add(event.getId(), part.partNumber(), part.brand(), part.description(),
                    part.quantity(), actor.id(), now)));
        }
        parts.flush();
        publishPartsAdded(event, saved, actor, now);
        return assembler.toView(event);
    }

    private void publishPartsAdded(RepairEvent event, List<RepairPart> added, AuthenticatedUser actor, Instant now) {
        added.forEach(part -> events.publishEvent(new RepairEvents.RepairPartAdded(event.getId(), part.getId(),
                actor.id(), part.getPartNumber(), part.getBrand(), part.getDescription(), part.getQuantity(), now)));
    }
}
