package com.repairtrack.repair.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.repair.RepairEventType;

/** Inputs of the repair use cases. None of them carries source type or verification status. */
public final class Commands {

    private Commands() {
    }

    /** {@code garageId} null: the caller records as owner. Set: the caller records for that garage. */
    public record CreateRepair(RepairEventType eventType, LocalDate eventDate, int mileage, String title,
                               String description, UUID garageId, List<AddPart> parts) {

        public CreateRepair {
            parts = parts == null ? List.of() : List.copyOf(parts);
        }
    }

    public record AddPart(String partNumber, String brand, String description, int quantity) {
    }
}
