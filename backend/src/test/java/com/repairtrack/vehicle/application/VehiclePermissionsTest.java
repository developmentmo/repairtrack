package com.repairtrack.vehicle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.domain.VehicleDetails;

@ExtendWith(MockitoExtension.class)
class VehiclePermissionsTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    @Mock
    private GarageAccessService garageAccess;

    private VehiclePermissions permissions;
    private final UUID garageId = UUID.randomUUID();
    private final AuthenticatedUser owner = user(Role.OWNER);
    private final AuthenticatedUser mechanic = user(Role.OWNER);
    private final AuthenticatedUser stranger = user(Role.OWNER);
    private final AuthenticatedUser systemAdmin = user(Role.OWNER, Role.SYSTEM_ADMIN);
    private Vehicle garageRegistered;

    @BeforeEach
    void setUp() {
        permissions = new VehiclePermissions(garageAccess);
        garageRegistered = Vehicle.register("WVWZZZ1KZAW123456",
                new VehicleDetails(null, "VW", "Golf", 2010, null), mechanic.id(), garageId, NOW, TODAY);
    }

    @Test
    void ownerSeesVinAndCanEdit() {
        assertThat(permissions.canSeeVin(owner, garageRegistered, owner.id())).isTrue();
        assertThat(permissions.canEditDetails(owner, garageRegistered, owner.id())).isTrue();
    }

    @Test
    void strangerSeesNoVinAndCannotEdit() {
        when(garageAccess.isActiveMember(stranger.id(), garageId)).thenReturn(false);

        assertThat(permissions.canSeeVin(stranger, garageRegistered, owner.id())).isFalse();
        assertThat(permissions.canEditDetails(stranger, garageRegistered, null)).isFalse();
    }

    @Test
    void registeringGarageCanEditOnlyWhileVehicleHasNoOwner() {
        when(garageAccess.isActiveMember(mechanic.id(), garageId)).thenReturn(true);

        assertThat(permissions.canEditDetails(mechanic, garageRegistered, null)).isTrue();
        assertThat(permissions.canEditDetails(mechanic, garageRegistered, owner.id())).isFalse();
        assertThat(permissions.canSeeVin(mechanic, garageRegistered, owner.id())).isTrue();
    }

    @Test
    void systemAdminSeesAndEditsEverything() {
        assertThat(permissions.canSeeVin(systemAdmin, garageRegistered, owner.id())).isTrue();
        assertThat(permissions.canEditDetails(systemAdmin, garageRegistered, owner.id())).isTrue();
    }

    private static AuthenticatedUser user(Role... roles) {
        UUID id = UUID.randomUUID();
        return new AuthenticatedUser(id, id + "@example.com", Set.of(roles));
    }
}
