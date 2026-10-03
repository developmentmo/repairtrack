package com.repairtrack.sharing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.repairtrack.common.crypto.OpaqueTokens;
import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.dispute.DisputeDirectory;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.sharing.application.ShareViews.CreatedShare;
import com.repairtrack.sharing.domain.ShareStatus;
import com.repairtrack.sharing.domain.VehicleShare;
import com.repairtrack.sharing.infrastructure.SharingProperties;
import com.repairtrack.sharing.infrastructure.VehicleShareRepository;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.application.VehicleAccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    @Mock
    private VehicleShareRepository shares;
    @Mock
    private VehicleAccessService vehicleAccess;
    @Mock
    private DisputeDirectory disputeDirectory;
    @Mock
    private ApplicationEventPublisher events;

    private ShareService service;
    private final UUID vehicleId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), "o@example.com", Set.of(Role.OWNER));

    @BeforeEach
    void setUp() {
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Amsterdam"));
        service = new ShareService(shares, vehicleAccess, disputeDirectory,
                new SharingProperties("https://repairtrack.nl/", null), events, calendar);
    }

    @Test
    void ownerGetsALinkWhoseTokenIsStoredOnlyAsHash() {
        CreatedShare created = service.create(owner, vehicleId, 14, false);

        ArgumentCaptor<VehicleShare> saved = ArgumentCaptor.forClass(VehicleShare.class);
        verify(shares).save(saved.capture());
        assertThat(created.token()).hasSize(43);
        assertThat(created.url()).isEqualTo("https://repairtrack.nl/v/" + created.token());
        assertThat(created.url()).doesNotContain(vehicleId.toString()).doesNotContain(saved.getValue().getId().toString());
        assertThat(created.share().status()).isEqualTo(ShareStatus.ACTIVE);
        assertThat(created.share().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(created.toString()).doesNotContain(created.token());
        // what is stored is the hash, which cannot be turned back into the link
        assertThat(OpaqueTokens.sha256Hex(created.token())).hasSize(64);
    }

    @Test
    void defaultValidityIsThirtyDays() {
        CreatedShare created = service.create(owner, vehicleId, null, false);

        assertThat(created.share().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void nonOwnerCannotShare() {
        doThrow(new VehicleAccessDeniedException("no")).when(vehicleAccess).requireActiveOwner(owner, vehicleId);

        assertThatThrownBy(() -> service.create(owner, vehicleId, 30, false))
                .isInstanceOf(VehicleAccessDeniedException.class);
        verify(shares, never()).save(any());
    }
}
