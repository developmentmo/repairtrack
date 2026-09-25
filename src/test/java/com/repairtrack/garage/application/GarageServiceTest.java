package com.repairtrack.garage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.domain.Garage;
import com.repairtrack.garage.domain.GarageUser;
import com.repairtrack.garage.infrastructure.GarageRepository;
import com.repairtrack.garage.infrastructure.GarageUserRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;

@ExtendWith(MockitoExtension.class)
class GarageServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @Mock
    private GarageRepository garages;
    @Mock
    private GarageUserRepository memberships;
    @Mock
    private GarageAccessService access;
    @Mock
    private ApplicationEventPublisher events;

    private GarageService service;
    private AuthenticatedUser owner;
    private AuthenticatedUser systemAdmin;

    @BeforeEach
    void setUp() {
        service = new GarageService(garages, memberships, access, events, Clock.fixed(NOW, ZoneOffset.UTC));
        owner = new AuthenticatedUser(UUID.randomUUID(), "owner@example.com", Set.of(Role.OWNER));
        systemAdmin = new AuthenticatedUser(UUID.randomUUID(), "ops@example.com", Set.of(Role.OWNER, Role.SYSTEM_ADMIN));
    }

    @Test
    void registrationMakesTheCallerTheFirstGarageAdmin() {
        GarageView view = service.register(owner, new RegisterGarageCommand("Garage Jansen", "12345678",
                "Dorpsstraat 1", "1234 AB", "Utrecht", null, null));

        ArgumentCaptor<GarageUser> membership = ArgumentCaptor.forClass(GarageUser.class);
        verify(memberships).save(membership.capture());
        assertThat(membership.getValue().getUserId()).isEqualTo(owner.id());
        assertThat(membership.getValue().getRole()).isEqualTo(GarageRole.GARAGE_ADMIN);
        assertThat(membership.getValue().getGarageId()).isEqualTo(view.id());
        assertThat(view.verificationStatus()).isEqualTo(GarageVerificationStatus.PENDING);
    }

    @Test
    void onlySystemAdminCanDecideVerification() {
        UUID garageId = UUID.randomUUID();

        assertThatThrownBy(() -> service.decideVerification(owner, garageId, GarageVerificationStatus.VERIFIED, null))
                .isInstanceOf(SystemAdminRequiredException.class);
        verify(garages, never()).findByIdForUpdate(any());
    }

    @Test
    void systemAdminVerifiesPendingGarage() {
        Garage garage = Garage.register("G", "12345678", "Straat 1", "1234AB", "Utrecht", null, null, owner.id(), NOW);
        when(garages.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        GarageView view = service.decideVerification(systemAdmin, garage.getId(),
                GarageVerificationStatus.VERIFIED, "KvK checked");

        assertThat(view.verificationStatus()).isEqualTo(GarageVerificationStatus.VERIFIED);
        assertThat(garage.getVerificationChangedBy()).isEqualTo(systemAdmin.id());
    }
}
