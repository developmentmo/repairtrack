package com.repairtrack.vehicle.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.vehicle.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    /** Expects a normalized VIN. */
    Optional<Vehicle> findByVin(String vin);

    boolean existsByVin(String vin);

    /** Expects a normalized plate. Not unique over time, hence a list. */
    List<Vehicle> findByLicensePlate(String licensePlate);

    /** Serializes ownership changes of one vehicle (claim, end). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);
}
