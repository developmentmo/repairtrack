package com.repairtrack.repair.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.repairtrack.garage.GarageDirectory;
import com.repairtrack.garage.GarageSummary;
import com.repairtrack.repair.application.Views.CorrectionView;
import com.repairtrack.repair.application.Views.PartView;
import com.repairtrack.repair.application.Views.RepairView;
import com.repairtrack.repair.domain.RepairCorrection;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.domain.RepairPart;
import com.repairtrack.repair.infrastructure.RepairCorrectionRepository;
import com.repairtrack.repair.infrastructure.RepairPartRepository;

/** Builds views for many events with a fixed number of queries (no N+1). */
@Component
class RepairViewAssembler {

    private final RepairPartRepository parts;
    private final RepairCorrectionRepository corrections;
    private final GarageDirectory garageDirectory;

    RepairViewAssembler(RepairPartRepository parts, RepairCorrectionRepository corrections,
                        GarageDirectory garageDirectory) {
        this.parts = parts;
        this.corrections = corrections;
        this.garageDirectory = garageDirectory;
    }

    RepairView toView(RepairEvent event) {
        return toViews(List.of(event)).getFirst();
    }

    List<RepairView> toViews(Collection<RepairEvent> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = events.stream().map(RepairEvent::getId).collect(Collectors.toSet());
        Map<UUID, List<RepairPart>> partsByEvent = parts.findByRepairEventIdInOrderByCreatedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(RepairPart::getRepairEventId));
        Map<UUID, List<RepairCorrection>> correctionsByEvent = corrections
                .findByRepairEventIdInOrderByCreatedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(RepairCorrection::getRepairEventId));

        Set<UUID> garageIds = new HashSet<>();
        events.stream().map(RepairEvent::getGarageId).filter(Objects::nonNull).forEach(garageIds::add);
        correctionsByEvent.values().stream().flatMap(List::stream)
                .map(RepairCorrection::getCorrectedByGarageId).filter(Objects::nonNull).forEach(garageIds::add);
        Map<UUID, GarageSummary> garages = garageDirectory.findSummaries(garageIds);

        return events.stream().map(event -> new RepairView(
                event.getId(),
                event.getVehicleId(),
                event.getEventType(),
                event.getEventDate(),
                event.getMileage(),
                event.getTitle(),
                event.getDescription(),
                event.getSourceType(),
                event.getVerificationStatus(),
                event.getStatus(),
                event.getGarageId() == null ? null : garages.get(event.getGarageId()),
                partsByEvent.getOrDefault(event.getId(), List.of()).stream()
                        .map(p -> new PartView(p.getId(), p.getPartNumber(), p.getBrand(), p.getDescription(),
                                p.getQuantity()))
                        .toList(),
                correctionsByEvent.getOrDefault(event.getId(), List.of()).stream()
                        .map(c -> new CorrectionView(c.getField(), c.getOldValue(), c.getNewValue(), c.getReason(),
                                c.getCorrectedByGarageId() == null ? null : garages.get(c.getCorrectedByGarageId()),
                                c.getCreatedAt()))
                        .toList(),
                event.getVoidedAt(),
                event.getVoidReason(),
                event.getCreatedAt(),
                event.getUpdatedAt())).toList();
    }
}
