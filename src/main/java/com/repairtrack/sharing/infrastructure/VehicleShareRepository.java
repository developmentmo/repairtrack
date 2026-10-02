package com.repairtrack.sharing.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.repairtrack.sharing.domain.VehicleShare;

public interface VehicleShareRepository extends JpaRepository<VehicleShare, UUID> {

    Optional<VehicleShare> findByTokenHash(String tokenHash);

    List<VehicleShare> findByVehicleIdAndCreatedByOrderByCreatedAtDesc(UUID vehicleId, UUID createdBy);

    /**
     * Atomic increment without optimistic locking, so concurrent views of one link never fail
     * each other with version conflicts.
     */
    @Modifying
    @Query("""
            update VehicleShare s
               set s.accessCount = s.accessCount + 1, s.lastAccessedAt = :now
             where s.id = :id""")
    void recordAccess(@Param("id") UUID id, @Param("now") Instant now);
}
