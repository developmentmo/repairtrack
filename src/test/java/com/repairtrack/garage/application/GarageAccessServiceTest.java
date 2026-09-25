package com.repairtrack.garage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.GarageWorkPermit;
import com.repairtrack.garage.domain.Garage;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;

@ExtendWith(MockitoExtension.class)
class GarageAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private GarageRepository garages;
    @Mock
    private GarageUserRepository memberships;

    private GarageAccessService access;
    private Garage garage;
    private AuthenticatedUser mechanic;

    @BeforeEach
    void setUp() {
        access = new GarageAccessService(garages, memberships);
        garage = Garage.register("Garage", "12345678", "Straat 1", "1234AB", "Utrecht", null, null,
                UUID.randomUUID(), NOW);
        mechanic = user(Role.OWNER);
    }

    @Test
    void mechanicOfUnverifiedGarageMayRecordWorkAsUnverifiedSource() {
        givenGarage();
        givenMembership(mechanic, GarageRole.MECHANIC);

        GarageWorkPermit permit = access.validateCanCreateRepair(mechanic, garage.getId());

        assertThat(permit.role()).isEqualTo(GarageRole.MECHANIC);
        assertThat(permit.garageVerificationStatus()).isEqualTo(GarageVerificationStatus.PENDING);
        assertThat(permit.garageVerified()).isFalse();
    }

    @Test
    void mechanicOfVerifiedGarageGetsVerifiedPermit() {
        garage.decideVerification(GarageVerificationStatus.VERIFIED, UUID.randomUUID(), null, NOW);
        givenGarage();
        givenMembership(mechanic, GarageRole.MECHANIC);

        assertThat(access.validateCanCreateRepair(mechanic, garage.getId()).garageVerified()).isTrue();
    }

    @Test
    void nonMemberMayNotRecordWork() {
        givenGarage();
        givenNoMembership(mechanic);

        assertThatThrownBy(() -> access.validateCanCreateRepair(mechanic, garage.getId()))
                .isInstanceOf(GarageAccessDeniedException.class);
    }

    @Test
    void systemAdminIsNotImplicitlyAGarageMember() {
        AuthenticatedUser admin = user(Role.OWNER, Role.SYSTEM_ADMIN);
        givenGarage();
        givenNoMembership(admin);

        assertThatThrownBy(() -> access.validateCanCreateRepair(admin, garage.getId()))
                .isInstanceOf(GarageAccessDeniedException.class);
    }

    @Test
    void suspendedGarageMayNotRecordWork() {
        garage.decideVerification(GarageVerificationStatus.VERIFIED, UUID.randomUUID(), null, NOW);
        garage.decideVerification(GarageVerificationStatus.SUSPENDED, UUID.randomUUID(), null, NOW);
        givenGarage();
        givenMembership(mechanic, GarageRole.MECHANIC);

        assertThatThrownBy(() -> access.validateCanCreateRepair(mechanic, garage.getId()))
                .isInstanceOf(GarageSuspendedException.class);
    }

    @Test
    void unknownGarageIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(garages.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.validateCanCreateRepair(mechanic, unknown))
                .isInstanceOf(GarageNotFoundException.class);
    }

    @Test
    void mechanicIsNotGarageAdmin() {
        when(garages.existsById(garage.getId())).thenReturn(true);
        givenMembership(mechanic, GarageRole.MECHANIC);

        assertThatThrownBy(() -> access.requireGarageAdmin(mechanic, garage.getId()))
                .isInstanceOf(GarageAccessDeniedException.class);
    }

    @Test
    void garageAdminPassesAdminCheck() {
        when(garages.existsById(garage.getId())).thenReturn(true);
        givenMembership(mechanic, GarageRole.GARAGE_ADMIN);

        assertThatCode(() -> access.requireGarageAdmin(mechanic, garage.getId())).doesNotThrowAnyException();
    }

    @Test
    void systemAdminMayViewMembersWithoutMembership() {
        AuthenticatedUser admin = user(Role.OWNER, Role.SYSTEM_ADMIN);
        when(garages.existsById(garage.getId())).thenReturn(true);

        assertThatCode(() -> access.requireMemberOrSystemAdmin(admin, garage.getId())).doesNotThrowAnyException();
    }

    private void givenGarage() {
        when(garages.findById(garage.getId())).thenReturn(Optional.of(garage));
    }

    private void givenMembership(AuthenticatedUser user, GarageRole role) {
        when(memberships.findByGarageIdAndUserIdAndStatus(garage.getId(), user.id(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(GarageUser.add(garage.getId(), user.id(), role, UUID.randomUUID(), NOW)));
    }

    private void givenNoMembership(AuthenticatedUser user) {
        when(memberships.findByGarageIdAndUserIdAndStatus(garage.getId(), user.id(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    private static AuthenticatedUser user(Role... roles) {
        UUID id = UUID.randomUUID();
        return new AuthenticatedUser(id, id + "@example.com", Set.of(roles));
    }
}
