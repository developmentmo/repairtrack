package com.repairtrack.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentEvents;
import com.repairtrack.document.application.DocumentViews.PhotoView;
import com.repairtrack.document.domain.FileTooLargeException;
import com.repairtrack.document.domain.UnsupportedFileTypeException;
import com.repairtrack.document.domain.VehiclePhoto;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.MalwareScanner;
import com.repairtrack.document.infrastructure.StorageProperties;
import com.repairtrack.document.infrastructure.VehiclePhotoRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.security.Role;
import com.repairtrack.vehicle.VehicleAccessService;
import com.repairtrack.vehicle.application.VehicleAccessDeniedException;

@ExtendWith(MockitoExtension.class)
class VehiclePhotoServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] WEBP = "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

    @Mock
    private VehiclePhotoRepository photos;
    @Mock
    private DocumentStorage storage;
    @Mock
    private VehicleAccessService vehicleAccess;
    @Mock
    private ApplicationEventPublisher events;

    /** Flags content that contains the test marker, like FakeMalwareScanner in the integration tests. */
    private final MalwareScanner scanner = content -> {
        try {
            return new String(content.readAllBytes(), StandardCharsets.ISO_8859_1).contains("MALWARE-MARKER")
                    ? MalwareScanner.ScanResult.infected("Test.Marker")
                    : MalwareScanner.ScanResult.CLEAN;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    };

    private VehiclePhotoService service;
    private final UUID vehicleId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(UUID.randomUUID(), "o@example.com", Set.of(Role.OWNER));

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties("http://localhost:3900", null, "garage", "GK", "s",
                "bucket", true, Duration.ofMinutes(5), DataSize.ofKilobytes(1));
        BusinessCalendar calendar = new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Europe/Amsterdam"));
        service = new VehiclePhotoService(photos, storage, scanner, properties, vehicleAccess, events, calendar);
    }

    @Test
    void firstPhotoIsStoredWithItsSha256AndAudited() throws Exception {
        consumeStreamOnPut();
        presignAnything();
        when(photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, owner.id(), VehiclePhoto.Status.ACTIVE))
                .thenReturn(Optional.empty());

        PhotoView view = service.upload(owner, vehicleId, file("mijn auto.jpg", JPEG));

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JPEG));
        assertThat(view.sha256()).isEqualTo(expected);
        assertThat(view.mimeType()).isEqualTo("image/jpeg");
        assertThat(view.downloadUrl()).isNotNull();
        assertThat(view.downloadUrlExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        ArgumentCaptor<VehiclePhoto> saved = ArgumentCaptor.forClass(VehiclePhoto.class);
        verify(photos).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStorageKey())
                .isEqualTo("vehicles/" + vehicleId + "/photos/" + saved.getValue().getId())
                .doesNotContain("auto");
        assertThat(saved.getValue().getUploadedBy()).isEqualTo(owner.id());
        ArgumentCaptor<DocumentEvents.VehiclePhotoUploaded> event =
                ArgumentCaptor.forClass(DocumentEvents.VehiclePhotoUploaded.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().replacedPhotoId()).isNull();
        assertThat(event.getValue().sha256()).isEqualTo(expected);
    }

    @Test
    void replacingKeepsThePreviousPhotoAsReplaced() {
        consumeStreamOnPut();
        presignAnything();
        VehiclePhoto previous = VehiclePhoto.create(UUID.randomUUID(), vehicleId, owner.id(),
                "vehicles/" + vehicleId + "/photos/old", "image/png", 10, "a".repeat(64), NOW.minusSeconds(3600));
        when(photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, owner.id(), VehiclePhoto.Status.ACTIVE))
                .thenReturn(Optional.of(previous));

        PhotoView view = service.upload(owner, vehicleId, file("nieuw.webp", WEBP));

        assertThat(view.mimeType()).isEqualTo("image/webp");
        assertThat(previous.getStatus()).isEqualTo(VehiclePhoto.Status.REPLACED);
        assertThat(previous.getReplacedById()).isEqualTo(view.id());
        assertThat(previous.getReplacedAt()).isEqualTo(NOW);
        InOrder order = inOrder(photos);
        order.verify(photos).saveAndFlush(previous);
        order.verify(photos).saveAndFlush(any(VehiclePhoto.class));
        verify(storage, never()).deleteQuietly(anyString());
        ArgumentCaptor<DocumentEvents.VehiclePhotoUploaded> event =
                ArgumentCaptor.forClass(DocumentEvents.VehiclePhotoUploaded.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().replacedPhotoId()).isEqualTo(previous.getId());
    }

    @Test
    void onlyTheCurrentOwnerCanUpload() {
        doThrow(new VehicleAccessDeniedException("no")).when(vehicleAccess).requireActiveOwner(owner, vehicleId);

        assertThatThrownBy(() -> service.upload(owner, vehicleId, file("f.jpg", JPEG)))
                .isInstanceOf(VehicleAccessDeniedException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
        verify(photos, never()).saveAndFlush(any());
    }

    @Test
    void onlyImagesAreAccepted() {
        byte[] pdf = "%PDF-1.4\n%not a photo\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> service.upload(owner, vehicleId, file("auto.jpg", pdf)))
                .isInstanceOf(UnsupportedFileTypeException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void oversizedPhotosAreRejected() {
        assertThatThrownBy(() -> service.upload(owner, vehicleId, file("big.jpg", new byte[2048])))
                .isInstanceOf(FileTooLargeException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void malwareIsRejectedBeforeAnythingIsStoredAndReported() {
        byte[] infected = new byte[JPEG.length + 20];
        System.arraycopy(JPEG, 0, infected, 0, JPEG.length);
        System.arraycopy("MALWARE-MARKER".getBytes(StandardCharsets.US_ASCII), 0, infected, JPEG.length, 14);

        assertThatThrownBy(() -> service.upload(owner, vehicleId, file("f.jpg", infected)))
                .isInstanceOf(MalwareDetectedException.class);
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
        verify(photos, never()).saveAndFlush(any());
        verify(events).publishEvent(any(DocumentEvents.VehiclePhotoRejectedAsMalware.class));
    }

    @Test
    void aConcurrentUploadIsReportedAsAConflict() {
        consumeStreamOnPut();
        when(photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, owner.id(), VehiclePhoto.Status.ACTIVE))
                .thenReturn(Optional.empty());
        when(photos.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_vehicle_photo_active"));

        assertThatThrownBy(() -> service.upload(owner, vehicleId, file("f.jpg", JPEG)))
                .isInstanceOf(VehiclePhotoConflictException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void currentReturnsOnlyTheCallersOwnPhoto() {
        presignAnything();
        VehiclePhoto photo = VehiclePhoto.create(UUID.randomUUID(), vehicleId, owner.id(), "k", "image/png", 10,
                "b".repeat(64), NOW);
        when(photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, owner.id(), VehiclePhoto.Status.ACTIVE))
                .thenReturn(Optional.of(photo));

        PhotoView view = service.current(owner, vehicleId);

        assertThat(view.id()).isEqualTo(photo.getId());
        verify(vehicleAccess).requireActiveOwner(owner, vehicleId);
        verify(storage).presignDownload(eq("k"), eq("vehicle-photo.png"), eq("image/png"), any());
    }

    @Test
    void currentWithoutPhotoIsNotFound() {
        when(photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, owner.id(), VehiclePhoto.Status.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current(owner, vehicleId)).isInstanceOf(VehiclePhotoNotFoundException.class);
    }

    @Test
    void currentRequiresTheCurrentOwner() {
        doThrow(new VehicleAccessDeniedException("no")).when(vehicleAccess).requireActiveOwner(owner, vehicleId);

        assertThatThrownBy(() -> service.current(owner, vehicleId)).isInstanceOf(VehicleAccessDeniedException.class);
        verify(photos, never()).findByVehicleIdAndUploadedByAndStatus(any(), any(), any());
    }

    /** The real storage reads the stream (and thereby drives the digest); the mock must do the same. */
    private void consumeStreamOnPut() {
        doAnswer(invocation -> {
            ((InputStream) invocation.getArgument(1)).readAllBytes();
            return null;
        }).when(storage).put(anyString(), any(), anyLong(), anyString());
    }

    private void presignAnything() {
        try {
            when(storage.presignDownload(anyString(), anyString(), anyString(), any()))
                    .thenReturn(URI.create("http://localhost:3900/bucket/key?X-Amz-Signature=x").toURL());
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IncomingFile file(String name, byte[] content) {
        return new IncomingFile(name, content.length, () -> new ByteArrayInputStream(content));
    }
}
