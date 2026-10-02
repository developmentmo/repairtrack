package com.repairtrack.vehicle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.domain.InvalidOwnershipPeriodException;
import com.repairtrack.vehicle.domain.OwnershipStatus;
import com.repairtrack.vehicle.domain.Vehicle;
import com.repairtrack.vehicle.domain.VehicleDetails;
import com.repairtrack.vehicle.domain.VehicleOwnership;
import com.repairtrack.vehicle.infrastructure.VehicleOwnershipRepository;
import com.repairtrack.vehicle.infrastructure.VehicleRepository;

@ExtendWith(MockitoExtension.class)
class OwnershipServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final String VIN = "WVWZZZ1KZAW123456";

    @Mock
    private VehicleRepository vehicles;
    @Mock
    private VehicleOwnershipRepository ownerships;
    @Mock
    private ApplicationEventPublisher events;

    private OwnershipService service;
    private Vehicle vehicle;
    private AuthenticatedUser buyer;

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Amsterdam"));
        service = new OwnershipService(vehicles, ownerships, events, calendar);
        vehicle = Vehicle.register(VIN, new VehicleDetails("12ABC3", "VW", "Golf", 2010, null), UUID.randomUUID(),
                UUID.randomUUID(), NOW, TODAY);
        buyer = new AuthenticatedUser(UUID.randomUUID(), "buyer@example.com", Set.of(Role.OWNER));
        when(vehicles.findByIdForUpdate(vehicle.getId())).thenReturn(Optional.of(vehicle));
    }

    @Test
    void claimWithMatchingVinCreatesActiveOwnership() {
        when(ownerships.findByVehicleIdAndStatus(vehicle.getId(), OwnershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(ownerships.findLatestEndDate(vehicle.getId())).thenReturn(Optional.empty());

        OwnershipView view = service.claim(buyer, vehicle.getId(), "wvwzzz1kzaw123456", null);

        assertThat(view.status()).isEqualTo(OwnershipStatus.ACTIVE);
        assertThat(view.startDate()).isEqualTo(TODAY);
        verify(ownerships).saveAndFlush(any(VehicleOwnership.class));
    }

    @Test
    void claimWithWrongVinIsRefused() {
        assertThatThrownBy(() -> service.claim(buyer, vehicle.getId(), "WVWZZZ1KZAW999999", null))
                .isInstanceOf(OwnershipProofInvalidException.class);
        verify(ownerships, never()).saveAndFlush(any());
    }

    @Test
    void claimOfOwnedVehicleIsRefused() {
        VehicleOwnership current = VehicleOwnership.start(vehicle.getId(), UUID.randomUUID(), TODAY.minusYears(1),
                NOW, TODAY);
        when(ownerships.findByVehicleIdAndStatus(vehicle.getId(), OwnershipStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.claim(buyer, vehicle.getId(), VIN, null))
                .isInstanceOf(VehicleAlreadyOwnedException.class);
    }

    @Test
    void ownershipCannotOverlapThePreviousOne() {
        when(ownerships.findByVehicleIdAndStatus(vehicle.getId(), OwnershipStatus.ACTIVE)).thenReturn(Optional.empty());
        when(ownerships.findLatestEndDate(vehicle.getId())).thenReturn(Optional.of(TODAY.minusDays(5)));

        assertThatThrownBy(() -> service.claim(buyer, vehicle.getId(), VIN, TODAY.minusDays(30)))
                .isInstanceOf(InvalidOwnershipPeriodException.class);
    }

    @Test
    void onlyTheCurrentOwnerCanEndOwnership() {
        VehicleOwnership current = VehicleOwnership.start(vehicle.getId(), UUID.randomUUID(), TODAY.minusYears(1),
                NOW, TODAY);
        when(ownerships.findByVehicleIdAndStatus(vehicle.getId(), OwnershipStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.endOwnership(buyer, vehicle.getId(), null))
                .isInstanceOf(VehicleAccessDeniedException.class);
        assertThat(current.isActive()).isTrue();
    }
}
