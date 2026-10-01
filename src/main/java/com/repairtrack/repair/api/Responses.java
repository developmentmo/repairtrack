package com.repairtrack.repair.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.repairtrack.garage.GarageSummary;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.mileage.MileageAnomaly;
import com.repairtrack.mileage.MileageHistory;
import com.repairtrack.mileage.MileageReading;
import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.application.Views.CorrectionView;
import com.repairtrack.repair.application.Views.PartView;
import com.repairtrack.repair.application.Views.RepairView;
import com.repairtrack.repair.domain.CorrectableField;
import com.repairtrack.repair.domain.RepairStatus;
import com.repairtrack.vehicle.VehicleSummary;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

public final class Responses {

    private Responses() {
    }

    public record GarageInfo(UUID id, String name, String city, GarageVerificationStatus verificationStatus) {

        static GarageInfo from(GarageSummary garage) {
            return garage == null ? null
                    : new GarageInfo(garage.id(), garage.name(), garage.city(), garage.verificationStatus());
        }
    }

    public record PartResponse(UUID id, String partNumber, String brand, String description, int quantity) {

        static PartResponse from(PartView part) {
            return new PartResponse(part.id(), part.partNumber(), part.brand(), part.description(), part.quantity());
        }
    }

    /** {@code correctedByGarage} null means: corrected by the vehicle owner. */
    public record CorrectionResponse(CorrectableField field, String originalValue, String correctedValue, String reason,
                              GarageInfo correctedByGarage, Instant correctedAt) {

        static CorrectionResponse from(CorrectionView c) {
            return new CorrectionResponse(c.field(), c.oldValue(), c.newValue(), c.reason(),
                    GarageInfo.from(c.correctedByGarage()), c.correctedAt());
        }
    }

    public record ReadingResponse(LocalDate date, int mileage, SourceType sourceType) {

        static ReadingResponse from(MileageReading reading) {
            return new ReadingResponse(reading.recordedDate(), reading.mileage(), reading.sourceType());
        }
    }

    public record MileageWarningResponse(String code, String message, ReadingResponse earlier, ReadingResponse later) {

        static MileageWarningResponse from(MileageAnomaly anomaly) {
            return new MileageWarningResponse(anomaly.code(), anomaly.message(),
                    ReadingResponse.from(anomaly.earlier()), ReadingResponse.from(anomaly.later()));
        }
    }

    /**
     * A history entry. {@code garage} is null for owner records. {@code warnings} is only filled on
     * create/correct responses (non-blocking mileage inconsistencies).
     */
    public record RepairResponse(
            UUID id,
            UUID vehicleId,
            RepairEventType eventType,
            LocalDate eventDate,
            int mileage,
            String title,
            String description,
            SourceType sourceType,
            VerificationStatus verificationStatus,
            RepairStatus status,
            GarageInfo garage,
            List<PartResponse> parts,
            List<CorrectionResponse> corrections,
            Instant voidedAt,
            String voidReason,
            Instant createdAt,
            Instant updatedAt,
            List<MileageWarningResponse> warnings
    ) {

        static RepairResponse from(RepairView view) {
            return from(view, List.of());
        }

        static RepairResponse from(RepairView v, List<MileageAnomaly> warnings) {
            return new RepairResponse(v.id(), v.vehicleId(), v.eventType(), v.eventDate(), v.mileage(), v.title(),
                    v.description(), v.sourceType(), v.verificationStatus(), v.status(), GarageInfo.from(v.garage()),
                    v.parts().stream().map(PartResponse::from).toList(),
                    v.corrections().stream().map(CorrectionResponse::from).toList(),
                    v.voidedAt(), v.voidReason(), v.createdAt(), v.updatedAt(),
                    warnings.stream().map(MileageWarningResponse::from).toList());
        }
    }

    public record MileageHistoryResponse(List<ReadingResponse> readings, List<MileageWarningResponse> anomalies) {

        static MileageHistoryResponse from(MileageHistory history) {
            return new MileageHistoryResponse(history.readings().stream().map(ReadingResponse::from).toList(),
                    history.anomalies().stream().map(MileageWarningResponse::from).toList());
        }
    }

    public record VehicleSummaryResponse(UUID id, String licensePlate, String make, String model, Integer modelYear) {

        static VehicleSummaryResponse from(VehicleSummary v) {
            return new VehicleSummaryResponse(v.id(), v.licensePlate(), v.make(), v.model(), v.modelYear());
        }
    }
}
