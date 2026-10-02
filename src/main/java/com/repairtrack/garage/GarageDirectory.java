package com.repairtrack.garage;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.garage.infrastructure.GarageRepository;

/** Read-only garage lookups for other modules. */
@Service
public class GarageDirectory {

    private final GarageRepository garages;

    public GarageDirectory(GarageRepository garages) {
        this.garages = garages;
    }

    @Transactional(readOnly = true)
    public Map<UUID, GarageSummary> findSummaries(Collection<UUID> garageIds) {
        if (garageIds.isEmpty()) {
            return Map.of();
        }
        return garages.findAllById(garageIds).stream()
                .map(g -> new GarageSummary(g.getId(), g.getName(), g.getCity(), g.getVerificationStatus()))
                .collect(Collectors.toMap(GarageSummary::id, Function.identity()));
    }
}
