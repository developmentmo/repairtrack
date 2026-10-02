package com.repairtrack.repair.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.VehicleDirectory;
import com.repairtrack.verification.Provenance;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationStatus;

@ExtendWith(MockitoExtension.class)
class RepairAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    @Mock
    private VehicleAccessService vehicleAccess;
    @Mock
    private VehicleDirectory vehicleDirectory;
    @Mock
    private GarageAccessService garageAccess;
    @Mock
    private RepairEventRepository repairs;

    private final UUID vehicleId = UUID.randomUUID();
    private final UUID garageA = UUID.randomUUID();
    private final AuthenticatedUser owner = user();
    private final AuthenticatedUser mechanicA = user();
    private final AuthenticatedUser mechanicB = user();

    private RepairAccessPolicy policy() {
        return new RepairAccessPolicy(vehicleAccess, vehicleDirectory, garageAccess, repairs);
    }

    @Test
    void ownerCannotModifyAGarageRecord() {
        RepairEvent garageRecord = garageRecord();
        when(garageAccess.isActiveMember(owner.id(), garageA)).thenReturn(false);

        assertThatThrownBy(() -> policy().requireCanModify(owner, garageRecord))
                .isInstanceOf(RepairAccessDeniedException.class);
    }

    @Test
    void mechanicOfAnotherGarageCannotModifyAGarageRecord() {
        RepairEvent garageRecord = garageRecord();
        when(garageAccess.isActiveMember(mechanicB.id(), garageA)).thenReturn(false);

        assertThatThrownBy(() -> policy().requireCanModify(mechanicB, garageRecord))
                .isInstanceOf(RepairAccessDeniedException.class);
    }

    @Test
    void mechanicOfTheRecordingGarageCanModifyOnBehalfOfIt() {
        RepairEvent garageRecord = garageRecord();
        when(garageAccess.isActiveMember(mechanicA.id(), garageA)).thenReturn(true);

        assertThat(policy().requireCanModify(mechanicA, garageRecord)).isEqualTo(garageA);
    }

    @Test
    void garageCannotModifyAnOwnerRecord() {
        RepairEvent ownerRecord = ownerRecord();

        assertThatThrownBy(() -> policy().requireCanModify(mechanicA, ownerRecord))
                .isInstanceOf(RepairAccessDeniedException.class);
    }

    @Test
    void ownerCanModifyOwnRecordOnlyWhileStillOwner() {
        RepairEvent ownerRecord = ownerRecord();
        when(vehicleAccess.isActiveOwner(owner.id(), vehicleId)).thenReturn(true, false);

        assertThat(policy().requireCanModify(owner, ownerRecord)).isNull();
        assertThatThrownBy(() -> policy().requireCanModify(owner, ownerRecord))
                .isInstanceOf(RepairAccessDeniedException.class);
    }

    @Test
    void systemAdminMayVoidAnything() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "ops@example.com",
                Set.of(Role.OWNER, Role.SYSTEM_ADMIN));

        assertThatCode(() -> policy().requireCanVoid(admin, garageRecord())).doesNotThrowAnyException();
    }

    @Test
    void unrelatedUserCannotViewHistory() {
        AuthenticatedUser stranger = user();
        when(vehicleAccess.isActiveOwner(stranger.id(), vehicleId)).thenReturn(false);
        when(garageAccess.activeGarageIds(stranger.id())).thenReturn(Set.of());

        assertThat(policy().canViewHistory(stranger, vehicleId)).isFalse();
    }

    @Test
    void garageThatWorkedOnTheVehicleCanViewHistory() {
        when(vehicleAccess.isActiveOwner(mechanicA.id(), vehicleId)).thenReturn(false);
        when(garageAccess.activeGarageIds(mechanicA.id())).thenReturn(Set.of(garageA));
        when(vehicleDirectory.registeringGarageId(vehicleId)).thenReturn(Optional.empty());
        when(repairs.existsByVehicleIdAndGarageIdIn(vehicleId, Set.of(garageA))).thenReturn(true);

        assertThat(policy().canViewHistory(mechanicA, vehicleId)).isTrue();
    }

    private RepairEvent garageRecord() {
        return RepairEvent.record(vehicleId, garageA, mechanicA.id(), RepairEventType.MAINTENANCE,
                new Provenance(SourceType.GARAGE, VerificationStatus.GARAGE_VERIFIED), TODAY, 1000, "Service",
                null, NOW, TODAY);
    }

    private RepairEvent ownerRecord() {
        return RepairEvent.record(vehicleId, null, owner.id(), RepairEventType.MAINTENANCE,
                new Provenance(SourceType.OWNER, VerificationStatus.UNVERIFIED), TODAY, 1000, "Oil change",
                null, NOW, TODAY);
    }

    private static AuthenticatedUser user() {
        UUID id = UUID.randomUUID();
        return new AuthenticatedUser(id, id + "@example.com", Set.of(Role.OWNER));
    }
}
