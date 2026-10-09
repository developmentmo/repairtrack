package com.repairtrack.document.api;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.repairtrack.document.application.DocumentViews.PhotoView;
import com.repairtrack.document.application.IncomingFile;
import com.repairtrack.document.application.VehiclePhotoService;
import com.repairtrack.security.AuthenticatedUser;

/**
 * The current owner's photo of their vehicle. Upload (and replace) is {@code multipart/form-data} with a
 * {@code file} part. There is no delete endpoint: a replaced photo is kept as REPLACED.
 */
@RestController
class VehiclePhotoController {

    private final VehiclePhotoService photoService;

    VehiclePhotoController(VehiclePhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(path = "/api/v1/vehicles/{vehicleId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    VehiclePhotoResponse upload(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable("vehicleId") UUID vehicleId,
                                @RequestPart("file") MultipartFile file) {
        // The multipart file is spooled by the servlet container, so it can be read more than once.
        var incoming = new IncomingFile(file.getOriginalFilename(), file.getSize(), file::getInputStream);
        return VehiclePhotoResponse.from(photoService.upload(actor, vehicleId, incoming));
    }

    @GetMapping("/api/v1/vehicles/{vehicleId}/photo")
    VehiclePhotoResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                             @PathVariable("vehicleId") UUID vehicleId) {
        return VehiclePhotoResponse.from(photoService.current(actor, vehicleId));
    }

    /** {@code downloadUrl} is a presigned URL to the private bucket, valid until {@code downloadUrlExpiresAt}. */
    public record VehiclePhotoResponse(UUID id, UUID vehicleId, String mimeType, long fileSize, String sha256,
                                       Instant uploadedAt, String downloadUrl, Instant downloadUrlExpiresAt) {

        static VehiclePhotoResponse from(PhotoView v) {
            return new VehiclePhotoResponse(v.id(), v.vehicleId(), v.mimeType(), v.fileSize(), v.sha256(),
                    v.uploadedAt(), v.downloadUrl().toString(), v.downloadUrlExpiresAt());
        }
    }
}
