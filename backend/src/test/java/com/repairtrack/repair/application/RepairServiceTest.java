package com.repairtrack.repair.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.garage.GarageAccessService;
import com.repairtrack.garage.GarageRole;
import com.repairtrack.garage.GarageVerificationStatus;
import com.repairtrack.garage.GarageWorkPermit;
import com.repairtrack.mileage.MileageService;
import com.repairtrack.repair.RepairEventType;
import com.repairtrack.repair.RepairEvents;
import com.repairtrack.repair.domain.RepairEvent;
import com.repairtrack.repair.infrastructure.RepairCorrectionRepository;
import com.repairtrack.repair.infrastructure.RepairEventRepository;
import com.repairtrack.repair.infrastructure.RepairPartRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.application.VehicleAccessDeniedException;
import com.repairtrack.verification.SourceType;
import com.repairtrack.verification.VerificationService;
import com.repairtrack.verification.VerificationStatus;

@ExtendWith(MockitoExtension.class)
class RepairServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private RepairEventRepository repairs;
    @Mock
    private RepairPartRepository parts;
    @Mock
    private RepairCorrectionRepository corrections;
    @Mock
    private RepairAccessPolicy access;
    @Mock
    private RepairViewAssembler assembler;
    @Mock
    private VehicleAccessService vehicleAccess;
    @Mock
    private GarageAccessService garageAccess;
    @Mock
    private MileageService mileage;
    @Mock
    private ApplicationEventPublisher events;

    private RepairService service;
    private final UUID vehicleId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "u@example.com", Set.of(Role.OWNER));

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Amsterdam"));
        service = new RepairService(repairs, parts, corrections, access, assembler, vehicleAccess, garageAccess,
                new VerificationService(), mileage, events, calendar);
    }

    @Test
    void ownerCreatesUnverifiedOwnerRecordAndMileageReading() {
        service.create(user, vehicleId, command(null));

        RepairEvent saved = savedEvent();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.OWNER);
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        verify(vehicleAccess).requireActiveOwner(user, vehicleId);
        verify(mileage).record(eq(vehicleId), eq(183_421), any(), eq(SourceType.OWNER), eq(saved.getId()), eq(NOW));
        verify(events).publishEvent(any(RepairEvents.RepairCreated.class));
    }

    @Test
    void nonOwnerWithoutGarageCannotCreate() {
        doThrow(new VehicleAccessDeniedException("no")).when(vehicleAccess).requireActiveOwner(user, vehicleId);

        assertThatThrownBy(() -> service.create(user, vehicleId, command(null)))
                .isInstanceOf(VehicleAccessDeniedException.class);
        verify(repairs, never()).save(any());
        verify(mileage, never()).record(any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void unverifiedGarageCreatesGarageRecord() {
        UUID garageId = UUID.randomUUID();
        when(garageAccess.validateCanRecordWork(user, garageId)).thenReturn(
                new GarageWorkPermit(garageId, user.id(), GarageRole.MECHANIC, GarageVerificationStatus.PENDING));

        service.create(user, vehicleId, command(garageId));

        RepairEvent saved = savedEvent();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.GARAGE);
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.GARAGE_VERIFIED);
        assertThat(saved.getGarageId()).isEqualTo(garageId);
        verify(vehicleAccess, never()).requireActiveOwner(any(), any());
    }

    @Test
    void verifiedGarageCreatesVerifiedGarageRecord() {
        UUID garageId = UUID.randomUUID();
        when(garageAccess.validateCanRecordWork(user, garageId)).thenReturn(
                new GarageWorkPermit(garageId, user.id(), GarageRole.MECHANIC, GarageVerificationStatus.VERIFIED));

        service.create(user, vehicleId, command(garageId));

        assertThat(savedEvent().getSourceType()).isEqualTo(SourceType.VERIFIED_GARAGE);
    }

    private RepairEvent savedEvent() {
        ArgumentCaptor<RepairEvent> captor = ArgumentCaptor.forClass(RepairEvent.class);
        verify(repairs).save(captor.capture());
        return captor.getValue();
    }

    private static Commands.CreateRepair command(UUID garageId) {
        return new Commands.CreateRepair(RepairEventType.REPAIR, LocalDate.of(2026, 9, 14), 183_421,
                "Brake replacement", null, garageId, List.of());
    }
}
