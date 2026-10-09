package com.repairtrack.document.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.common.time.BusinessCalendar;
import com.repairtrack.document.DocumentEvents;
import com.repairtrack.document.application.DocumentViews.PhotoView;
import com.repairtrack.document.domain.DetectedFileType;
import com.repairtrack.document.domain.UnsupportedFileTypeException;
import com.repairtrack.document.domain.VehiclePhoto;
import com.repairtrack.document.infrastructure.DocumentStorage;
import com.repairtrack.document.infrastructure.MalwareScanner;
import com.repairtrack.document.infrastructure.StorageProperties;
import com.repairtrack.document.infrastructure.VehiclePhotoRepository;
import com.repairtrack.security.AuthenticatedUser;
import com.repairtrack.vehicle.VehicleAccessService;

/**
 * The owner's photo of their vehicle. Only the current owner can add one, and each owner only ever sees their own
 * photo: after a sale neither the previous nor the next owner sees the other's photo.
 * <p>
 * Upload: authorize &rarr; check size &rarr; JPEG, PNG or WebP by content &rarr; scan for malware &rarr; stream to
 * object storage while computing SHA-256 &rarr; mark the previous photo REPLACED (kept, as is its stored object)
 * &rarr; store metadata &rarr; publish for the audit trail.
 */
@Service
public class VehiclePhotoService {

    private static final Logger log = LoggerFactory.getLogger(VehiclePhotoService.class);

    private final VehiclePhotoRepository photos;
    private final DocumentStorage storage;
    private final UploadPipeline pipeline;
    private final StorageProperties properties;
    private final VehicleAccessService vehicleAccess;
    private final ApplicationEventPublisher events;
    private final BusinessCalendar calendar;

    public VehiclePhotoService(VehiclePhotoRepository photos, DocumentStorage storage, MalwareScanner scanner,
                               StorageProperties properties, VehicleAccessService vehicleAccess,
                               ApplicationEventPublisher events, BusinessCalendar calendar) {
        this.photos = photos;
        this.storage = storage;
        this.pipeline = new UploadPipeline(storage, scanner, properties);
        this.properties = properties;
        this.vehicleAccess = vehicleAccess;
        this.events = events;
        this.calendar = calendar;
    }

    /** Adds or replaces the caller's photo. {@code noRollbackFor}: the audit entry of a malware rejection is kept. */
    @Transactional(noRollbackFor = MalwareDetectedException.class)
    public PhotoView upload(AuthenticatedUser actor, UUID vehicleId, IncomingFile file) {
        vehicleAccess.requireActiveOwner(actor, vehicleId);
        DetectedFileType type = pipeline.check(file, DetectedFileType.PHOTOS,
                () -> new UnsupportedFileTypeException("Only JPEG, PNG and WebP photos are accepted."));
        scanForMalware(actor, vehicleId, file);

        UUID photoId = UUID.randomUUID();
        String key = VehiclePhoto.storageKeyFor(vehicleId, photoId);
        String sha256 = pipeline.store(key, file, type);

        Instant now = calendar.now();
        Optional<VehiclePhoto> previous =
                photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, actor.id(), VehiclePhoto.Status.ACTIVE);
        VehiclePhoto photo = VehiclePhoto.create(photoId, vehicleId, actor.id(), key, type.mimeType(), file.size(),
                sha256, now);
        try {
            // The previous photo leaves ACTIVE first: one active photo per vehicle and owner (partial unique index).
            previous.ifPresent(p -> {
                p.replaceWith(photoId, now);
                photos.saveAndFlush(p);
            });
            photos.saveAndFlush(photo);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
            throw new VehiclePhotoConflictException();
        }

        events.publishEvent(new DocumentEvents.VehiclePhotoUploaded(photoId, vehicleId,
                previous.map(VehiclePhoto::getId).orElse(null), type.mimeType(), file.size(), sha256, actor.id(), now));
        return view(photo);
    }

    /** The caller's current photo with a short-lived download URL; the bucket itself is private. */
    @Transactional(readOnly = true)
    public PhotoView current(AuthenticatedUser actor, UUID vehicleId) {
        vehicleAccess.requireActiveOwner(actor, vehicleId);
        return photos.findByVehicleIdAndUploadedByAndStatus(vehicleId, actor.id(), VehiclePhoto.Status.ACTIVE)
                .map(this::view)
                .orElseThrow(VehiclePhotoNotFoundException::new);
    }

    private PhotoView view(VehiclePhoto photo) {
        String extension = DetectedFileType.fromMimeType(photo.getMimeType())
                .map(DetectedFileType::extension).orElse("bin");
        Instant expiresAt = calendar.now().plus(properties.presignedUrlTtl());
        return new PhotoView(photo.getId(), photo.getVehicleId(), photo.getMimeType(), photo.getFileSize(),
                photo.getSha256(), photo.getUploadedAt(),
                storage.presignDownload(photo.getStorageKey(), "vehicle-photo." + extension, photo.getMimeType(),
                        properties.presignedUrlTtl()),
                expiresAt);
    }

    /** Before anything is stored. Fails closed: no verdict means no upload. */
    private void scanForMalware(AuthenticatedUser actor, UUID vehicleId, IncomingFile file) {
        MalwareScanner.ScanResult result = pipeline.scan(file);
        if (!result.clean()) {
            log.warn("Vehicle photo rejected: malware '{}' (vehicle {}, user {})", result.signature(), vehicleId,
                    actor.id());
            events.publishEvent(new DocumentEvents.VehiclePhotoRejectedAsMalware(vehicleId, actor.id(),
                    result.signature(), calendar.now()));
            throw new MalwareDetectedException();
        }
    }
}
