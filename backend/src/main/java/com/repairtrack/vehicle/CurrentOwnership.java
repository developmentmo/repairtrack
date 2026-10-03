package com.repairtrack.vehicle;

import java.time.LocalDate;
import java.util.UUID;

/** The active ownership period of a vehicle, for the dispute module. */
public record CurrentOwnership(UUID ownershipId, UUID ownerId, LocalDate startDate) {
}
