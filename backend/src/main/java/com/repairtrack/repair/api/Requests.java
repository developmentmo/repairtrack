package com.repairtrack.repair.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.domain.RepairEvent;

/**
 * Request bodies. None of them has a source type or verification status field: the backend
 * decides those. "Not in the future" for dates is checked by the domain in Europe/Amsterdam time.
 */
public final class Requests {

    private Requests() {
    }

    /** {@code garageId}: record on behalf of this garage; omit to record as the vehicle owner. */
    public record CreateRepairRequest(
            @NotNull RepairEventType eventType,
            @NotNull LocalDate eventDate,
            @NotNull @PositiveOrZero @Max(RepairEvent.MAX_MILEAGE) Integer mileage,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            UUID garageId,
            @Size(max = 50) List<@Valid PartRequest> parts
    ) {
    }

    public record PartRequest(
            @Size(max = 100) String partNumber,
            @Size(max = 100) String brand,
            @NotBlank @Size(max = 500) String description,
            @NotNull @Min(1) @Max(999) Integer quantity
    ) {
    }

    public record AddPartsRequest(@NotEmpty @Size(max = 50) List<@Valid PartRequest> parts) {
    }

    public record VoidRepairRequest(@NotBlank @Size(max = 500) String reason) {
    }

    /** Only the fields to correct need to be present; {@code reason} is mandatory. */
    public record CorrectRepairRequest(
            RepairEventType eventType,
            LocalDate eventDate,
            @PositiveOrZero @Max(RepairEvent.MAX_MILEAGE) Integer mileage,
            @Size(max = 150) String title,
            @Size(max = 5000) String description,
            @NotBlank @Size(max = 500) String reason
    ) {
    }
}
