package com.repairtrack.garage.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;

public interface GarageUserRepository extends JpaRepository<GarageUser, UUID> {

    Optional<GarageUser> findByGarageIdAndUserIdAndStatus(UUID garageId, UUID userId, MembershipStatus status);

    boolean existsByGarageIdAndUserIdAndStatus(UUID garageId, UUID userId, MembershipStatus status);

    List<GarageUser> findByGarageIdAndStatus(UUID garageId, MembershipStatus status);

    List<GarageUser> findByUserIdAndStatus(UUID userId, MembershipStatus status);

    long countByGarageIdAndRoleAndStatus(UUID garageId, GarageRole role, MembershipStatus status);
}
