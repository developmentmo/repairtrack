package com.repairtrack.mileage.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.mileage.domain.MileageRecord;
import com.repairtrack.mileage.domain.MileageRecordStatus;

public interface MileageRecordRepository extends JpaRepository<MileageRecord, UUID> {

    List<MileageRecord> findByVehicleIdAndStatusOrderByRecordedDateAscCreatedAtAsc(UUID vehicleId,
                                                                                   MileageRecordStatus status);

    List<MileageRecord> findBySourceEventIdAndStatus(UUID sourceEventId, MileageRecordStatus status);
}
