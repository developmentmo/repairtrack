package com.repairtrack.garage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.domain.Garage;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.domain.MembershipStatus;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.security.UserDirectory;
import com.repairtrack.security.UserSummary;

@ExtendWith(MockitoExtension.class)
class GarageMemberServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private GarageRepository garages;
    @Mock
    private GarageUserRepository memberships;
    @Mock
    private GarageAccessService access;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private ApplicationEventPublisher events;

    private GarageMemberService service;
    private Garage garage;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new GarageMemberService(garages, memberships, access, userDirectory, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        admin = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Set.of(Role.OWNER));
        garage = Garage.register("Garage", "12345678", "Straat 1", "1234AB", "Utrecht", null, null, admin.id(), NOW);
        when(garages.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));
    }

    @Test
    void adminAddsExistingUserAsMechanic() {
        UserSummary mechanic = new UserSummary(UUID.randomUUID(), "mech@example.com", "Max", "Monteur", true);
        when(userDirectory.findActiveByEmail("mech@example.com")).thenReturn(Optional.of(mechanic));
        when(memberships.saveAndFlush(any(GarageUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GarageMemberView view = service.addMember(admin, garage.getId(), "mech@example.com", GarageRole.MECHANIC);

        assertThat(view.userId()).isEqualTo(mechanic.id());
        assertThat(view.role()).isEqualTo(GarageRole.MECHANIC);
        verify(access).requireGarageAdmin(admin, garage.getId());
    }

    @Test
    void nonAdminCannotAddMembers() {
        doThrow(new GarageAccessDeniedException("no")).when(access).requireGarageAdmin(admin, garage.getId());

        assertThatThrownBy(() -> service.addMember(admin, garage.getId(), "x@example.com", GarageRole.MECHANIC))
                .isInstanceOf(GarageAccessDeniedException.class);
        verify(memberships, never()).saveAndFlush(any());
    }

    @Test
    void unknownEmailIsReported() {
        when(userDirectory.findActiveByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(admin, garage.getId(), "ghost@example.com", GarageRole.MECHANIC))
                .isInstanceOf(UserNotRegisteredException.class);
    }

    @Test
    void existingMemberCannotBeAddedTwice() {
        UserSummary member = new UserSummary(UUID.randomUUID(), "m@example.com", "M", "M", true);
        when(userDirectory.findActiveByEmail("m@example.com")).thenReturn(Optional.of(member));
        when(memberships.existsByGarageIdAndUserIdAndStatus(garage.getId(), member.id(), MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.addMember(admin, garage.getId(), "m@example.com", GarageRole.MECHANIC))
                .isInstanceOf(AlreadyGarageMemberException.class);
    }

    @Test
    void lastGarageAdminCannotBeRemoved() {
        GarageUser adminMembership = GarageUser.add(garage.getId(), admin.id(), GarageRole.GARAGE_ADMIN, admin.id(), NOW);
        when(memberships.findByGarageIdAndUserIdAndStatus(garage.getId(), admin.id(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(adminMembership));
        when(memberships.countByGarageIdAndRoleAndStatus(garage.getId(), GarageRole.GARAGE_ADMIN,
                MembershipStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember(admin, garage.getId(), admin.id()))
                .isInstanceOf(LastGarageAdminException.class);
        assertThat(adminMembership.isActive()).isTrue();
    }

    @Test
    void memberCanLeaveWithoutBeingAdmin() {
        AuthenticatedUser mechanic = new AuthenticatedUser(UUID.randomUUID(), "m@example.com", Set.of(Role.OWNER));
        GarageUser membership = GarageUser.add(garage.getId(), mechanic.id(), GarageRole.MECHANIC, admin.id(), NOW);
        when(memberships.findByGarageIdAndUserIdAndStatus(garage.getId(), mechanic.id(), MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));

        service.removeMember(mechanic, garage.getId(), mechanic.id());

        assertThat(membership.isActive()).isFalse();
        verify(access, never()).requireGarageAdmin(any(), any());
    }

    @Test
    void mechanicCannotRemoveSomeoneElse() {
        AuthenticatedUser mechanic = new AuthenticatedUser(UUID.randomUUID(), "m@example.com", Set.of(Role.OWNER));
        doThrow(new GarageAccessDeniedException("no")).when(access).requireGarageAdmin(mechanic, garage.getId());

        assertThatThrownBy(() -> service.removeMember(mechanic, garage.getId(), admin.id()))
                .isInstanceOf(GarageAccessDeniedException.class);
    }
}
