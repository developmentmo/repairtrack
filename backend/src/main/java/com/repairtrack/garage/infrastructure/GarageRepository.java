package com.repairtrack.garage.infrastructure;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.garage.domain.Garage;

public interface GarageRepository extends JpaRepository<Garage, UUID> {

    /**
     * Locks the garage row. Used to serialize changes to a garage's membership and verification,
     * so invariants spanning several rows (e.g. "at least one GARAGE_ADMIN") cannot be broken by
     * concurrent requests.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Garage g where g.id = :id")
    Optional<Garage> findByIdForUpdate(@Param("id") UUID id);
}
