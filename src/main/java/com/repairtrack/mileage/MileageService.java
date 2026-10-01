package com.repairtrack.mileage;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.mileage.domain.MileageAnalyzer;
import com.repairtrack.mileage.domain.MileageRecord;
import com.repairtrack.mileage.domain.MileageRecordStatus;
import com.repairtrack.mileage.infrastructure.MileageRecordRepository;
import com.repairtrack.verification.SourceType;

/**
 * Mileage module API. Callers (the repair module) must have authorized the caller already;
 * this module only stores readings and analyses consistency. Runs inside the caller's transaction.
 */
@Service
public class MileageService {

    private final MileageRecordRepository records;

    public MileageService(MileageRecordRepository records) {
        this.records = records;
    }

    /**
     * Stores a reading and returns the anomalies that involve it. Never rejects a reading for
     * being inconsistent: the history must show what was registered, with a warning.
     */
    @Transactional
    public List<MileageAnomaly> record(UUID vehicleId, int mileage, LocalDate recordedDate, SourceType sourceType,
                                       UUID sourceEventId, Instant now) {
        MileageRecord record = MileageRecord.record(vehicleId, mileage, recordedDate, sourceType, sourceEventId, now);
        records.saveAndFlush(record);
        return history(vehicleId).anomalies().stream()
                .filter(anomaly -> anomaly.involves(record.getId()))
                .toList();
    }

    /** Voids the readings that came from a (voided or corrected) source event. */
    @Transactional
    public void voidForSourceEvent(UUID sourceEventId, Instant now) {
        records.findBySourceEventIdAndStatus(sourceEventId, MileageRecordStatus.ACTIVE)
                .forEach(record -> record.voidRecord(now));
    }

    @Transactional(readOnly = true)
    public MileageHistory history(UUID vehicleId) {
        List<MileageReading> readings = records
                .findByVehicleIdAndStatusOrderByRecordedDateAscCreatedAtAsc(vehicleId, MileageRecordStatus.ACTIVE)
                .stream()
                .map(MileageRecord::toReading)
                .toList();
        return new MileageHistory(readings, MileageAnalyzer.analyze(readings));
    }
}
